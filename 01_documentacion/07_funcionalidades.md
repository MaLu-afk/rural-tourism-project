# 7. Funcionalidades de la Aplicación

## Funcionalidad presentada en la primera entrega

La primera entrega del curso mostró el diseño en Figma de la aplicación y un proyecto base en Android Studio aún sin funcionalidad implementada: la estructura del prototipo, el flujo de pantallas y la pila tecnológica propuesta, pero ningún módulo operativo. El objetivo de esa etapa fue validar la propuesta de diseño y la viabilidad técnica del stack elegido antes de iniciar el desarrollo.

## Funcionalidades adicionales completadas

A partir de esa base, el equipo implementó la totalidad de los módulos descritos a continuación, organizados por área funcional.

### Arranque, autenticación y configuración inicial
- Pantalla de carga inicial (splash) y onboarding de bienvenida con selección de idioma (español, inglés, portugués, quechua).
- Registro e inicio de sesión con correo y contraseña, e inicio de sesión con Google.
- Verificación de cuenta por código (OTP) enviado al correo, y recuperación de contraseña por el mismo mecanismo.
- Configuración del perfil del negocio (nombre, tipo de emprendimiento) y carga inicial del catálogo de productos según esa categoría.

### Registro y gestión de visitas
- Formulario de alta de visita con selección de nacionalidad (con bandera), selección de servicios consumidos con cantidades, aplicación de descuentos (fijos o porcentuales) y cálculo automático de subtotal y total en la moneda preferida del negocio.
- Persistencia offline inmediata de cada visita en la base de datos local.
- Historial de visitas registradas y pantalla de detalle de cada una.

### Catálogo de productos
- Gestión completa del catálogo del negocio: alta, edición y eliminación de productos, con precio base, categoría y descuentos programables por fecha.

### Panel de insights (Dashboard)
- Gráficos profesionales (Vico) integrados tanto en la pantalla de inicio como en un panel dedicado, organizado en cuatro pestañas: Resumen (KPIs de visitantes, país líder, servicio estrella, ticket promedio e ingresos), Visitantes (ranking de nacionalidades y visitas por día de la semana), Ventas (ingresos en el tiempo y distribución de servicios) y Tiempos (horas de mayor afluencia).
- Filtro de periodo (todos, últimos 7 días, mes, año) compartido entre las cuatro pestañas.

### Mapa de procedencias
- Mapa offline (OpenStreetMap) con marcadores por país construidos a partir de un GeoJSON local, accesible también en modo de pantalla completa.

### Sincronización entre dispositivos sin Internet (P2P)
- Sincronización por red local mediante sockets TCP, con descubrimiento automático de dispositivos (NSD/mDNS) y emparejamiento por código QR.
- Pantallas de gestión de dispositivos vinculados y de estado de la sincronización en curso.
- El motor de sincronización corre como un proceso independiente de la pantalla, de modo que sigue activo aunque la aplicación esté en segundo plano.

### Sincronización con la nube
- Cuenta de usuario vinculada a un servidor en la nube propio, con sincronización incremental automática (subida de cambios pendientes y descarga de cambios remotos) cuando hay conexión a Internet.
- Actualización en tiempo real mediante WebSocket: si un cambio se origina en otro dispositivo vinculado a la misma cuenta, la aplicación lo refleja sin necesidad de que el usuario fuerce una sincronización manual.

### Notificaciones
- Notificaciones del sistema ante visitas o cambios sincronizados desde otro dispositivo, y notificación persistente y de baja prioridad mientras el servicio de sincronización P2P está activo.

### Síntesis de voz (texto a voz) multilingüe
- Reproducción de audio real (no simulada) mediante el motor offline Sherpa-ONNX, con un catálogo de voces en español, inglés, portugués y quechua del Cusco-Collao.
- Descarga bajo demanda de los modelos de voz desde la pantalla de Perfil, con indicador de progreso.
- Botón de audio reutilizable en las pantallas de insights y consejos, que se deshabilita con una indicación clara si el usuario no ha descargado previamente una voz para su idioma.

### Multimodalidad e idiomas
- Interfaz completa traducida a cuatro idiomas mediante recursos `strings.xml`.
- Una utilidad propia resuelve el contenido dinámico (no presente en los recursos de strings) según el idioma activo en tiempo de ejecución.

## Funcionalidades no incluidas en el alcance final

Por transparencia, se documentan dos funcionalidades planteadas en etapas tempranas del proyecto que no llegaron a implementarse dentro del plazo del curso:

- **Integración con una fuente externa de tendencias turísticas:** los consejos para el emprendedor provienen de contenido predefinido por categoría de negocio (hospedaje, alimentación, artesanía), no de un servicio externo de tendencias de mercado en tiempo real, como se planteaba en el reto original de MINCETUR.
- **Traducción automática de contenido dinámico generado por el usuario:** los nombres de productos o textos que el propio emprendedor introduce se muestran en el idioma en que fueron ingresados; no existe un modelo de traducción offline para ese contenido (a diferencia de la interfaz estática, que sí está traducida a los cuatro idiomas).

## Demostración completa de la app

La demostración completa de la aplicación, incluyendo el registro de una visita de extremo a extremo, la sincronización P2P entre dos dispositivos, la sincronización con la nube y la reproducción de audio en quechua, se hace en vivo durante la sustentación del proyecto.
