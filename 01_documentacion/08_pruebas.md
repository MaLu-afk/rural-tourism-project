# 8. Pruebas y Validación

Una vez que las funcionalidades principales de **Yupay Turismo** estuvieron implementadas, dedicamos una etapa del proyecto a comprobar que la aplicación se comportara como esperábamos. Como el público objetivo son emprendedores rurales con dispositivos de gama baja y conectividad intermitente, nos interesaba sobre todo validar dos cosas: que los flujos centrales (registrar una visita, ver los insights, sincronizar) funcionaran de principio a fin, y que la lógica que manipula dinero y fechas no introdujera errores silenciosos. Para ello combinamos pruebas manuales en un dispositivo real con pruebas automatizadas sobre las partes más críticas del código.

## Tipos de pruebas realizadas

### Pruebas manuales (funcionales) en dispositivo

La mayor parte de la validación se hizo de forma manual, instalando el APK en un teléfono Android físico y recorriendo cada pantalla como lo haría un usuario real. Este tipo de prueba fue el más útil durante el desarrollo, porque nos permitió detectar problemas de usabilidad y de comportamiento que no siempre son evidentes al leer el código. Probamos los siguientes flujos:

| # | Escenario probado | Resultado esperado |
|---|-------------------|--------------------|
| 1 | Registro e inicio de sesión (correo y Google) | El usuario se autentica y llega a la pantalla principal |
| 2 | Configuración inicial del perfil y del catálogo de productos | Los datos quedan guardados localmente y persisten al reabrir la app |
| 3 | Registro de una nueva visita (nacionalidad, productos, monto) | La visita se guarda sin conexión y aparece en el historial |
| 4 | Visualización del dashboard con gráficos (Vico) | Los gráficos reflejan las visitas registradas |
| 5 | Reproducción de audio de insights (TTS offline, español/quechua) | El texto se lee en voz alta con el modelo de voz descargado |
| 6 | Sincronización con la nube al recuperar conexión | Las visitas pendientes se suben y se marcan como sincronizadas |
| 7 | Sincronización entre dispositivos mediante código QR (P2P) | Dos teléfonos enlazan y comparten los registros |
| 8 | Uso de la app en modo avión (sin internet) | Las funciones de registro y consulta siguen operativas |

Estas pruebas se realizaron de forma exploratoria, sin un guion automatizado, anotando manualmente los casos en los que el comportamiento no era el esperado para corregirlos antes de la entrega.

### Pruebas unitarias (locales)

Para dar formalidad a la validación, incorporamos pruebas unitarias automatizadas que se ejecutan en la máquina de desarrollo (host JVM), sin necesidad de emulador. Nos enfocamos en la **lógica pura** del proyecto, es decir, en funciones que no dependen del framework de Android y que, por lo tanto, son rápidas y deterministas de probar. Implementamos dos conjuntos de pruebas con JUnit 4:

- **`CurrencyUtilsTest`** - verifica la conversión de montos entre las tres monedas que maneja la app (S/, $, €), tomando el Sol como moneda base. Cubre conversiones directas (soles a dólares y viceversa), conversiones cruzadas (dólar a euro pasando por soles) y el caso defensivo en el que un tipo de cambio es cero, comprobando que la función no produzca una división por cero.
- **`DateMappersTest`** - valida los conversores de fecha que se usan en la sincronización con el servidor (`parseIsoToMillis` y `millisToIso`), encargados de traducir entre el formato ISO 8601 que utiliza la API y el formato de epoch en milisegundos que almacena Room. Se probaron entradas con zona horaria explícita, sin zona, nulas, vacías y mal formadas.

### Pruebas instrumentadas

Adicionalmente, escribimos pruebas instrumentadas que se ejecutan sobre un emulador o dispositivo físico, divididas en dos grupos según lo que validan:

**Persistencia (Room):**
- **`VisitDaoTest`** - utiliza una base de datos Room **en memoria** para validar las operaciones del `VisitDao`: insertar una visita y recuperarla por su `uuid`, comprobar que una visita recién creada aparece correctamente en la lista de registros pendientes de sincronizar (`getUnsynced`), y verificar que el borrado total deja la tabla vacía. Al ser una base en memoria, cada prueba parte de un estado limpio y no afecta los datos reales del dispositivo.

**Interfaz (Jetpack Compose):**
- **`LoadingOverlayTest`** - usa `createComposeRule` para verificar que el componente `LoadingOverlay` muestra el mensaje recibido por parámetro, tanto el texto por defecto como uno personalizado, localizando el nodo por su texto (`onNodeWithText`) y comprobando su existencia en el árbol semántico.
- **`ServiceSelectorTest`** - verifica que la tarjeta de selección de servicio (`ServiceCard`, usada en el formulario de registro de visita) muestra el nombre del servicio recibido, y que al tocarla (`performClick`) se invoca el callback `onClick` correspondiente.

Ambos componentes de interfaz se eligieron por ser autocontenidos (no dependen de un ViewModel ni de un repositorio), lo que permitió probarlos de forma aislada sin necesidad de mocks adicionales.

## Resultados y retroalimentación

Las pruebas manuales confirmaron que los flujos principales de la aplicación funcionaban de extremo a extremo, incluyendo el funcionamiento sin conexión, que es uno de los requisitos centrales del proyecto. Durante este proceso identificamos y corregimos varios detalles de comportamiento y de interfaz antes de la entrega, lo que mejoró la estabilidad general de la app.

Las pruebas automatizadas, por su parte, nos dieron una red de seguridad sobre la lógica más sensible: la conversión de monedas, el manejo de fechas en la sincronización, la persistencia de visitas y el comportamiento de los componentes de interfaz más usados. Tener estas pruebas nos permitió modificar el código con mayor confianza, sabiendo que un cambio que rompiera estos cálculos o comportamientos se detectaría de inmediato al ejecutar la batería de pruebas.

La principal lección de esta etapa fue que las pruebas manuales, aunque indispensables para validar la experiencia de uso, no son suficientes por sí solas: son lentas de repetir y dependen de que recordemos revisar cada caso. Incorporar pruebas de interfaz con Jetpack Compose, además de las pruebas unitarias e instrumentadas de persistencia, confirmó esa idea: detectar errores en componentes reutilizables (como la tarjeta de selección de servicio) de forma automatizada resultó más rápido y confiable que revisarlos manualmente en cada cambio. De cara al trabajo futuro, el equipo considera prioritario extender esta cobertura de Compose a las pantallas completas (no solo a componentes aislados) y agregar más pruebas instrumentadas para los demás DAOs y para el motor de sincronización P2P.
