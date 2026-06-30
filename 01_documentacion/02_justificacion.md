# 2. Justificación del Proyecto

## Problema identificado

En las comunidades rurales del Perú que participan en la Estrategia Nacional de Turismo Comunitario, la información sobre los visitantes se registra manualmente en cuadernos. Este método presenta limitaciones importantes, documentadas en el reto planteado por MINCETUR y ProInnovate:

- **Datos incompletos y poco confiables:** los registros suelen estar incompletos porque no se exige al turista llenar todos los campos, y existe resistencia por parte de los emprendedores a compartir información de ingresos.
- **Digitalización lenta y costosa:** los especialistas de campo del MINCETUR visitan trimestralmente las comunidades, fotografían los cuadernos y transcriben los datos a una matriz de Excel, un proceso que consume tiempo y recursos.
- **Falta de retroalimentación útil para el emprendedor:** los reportes generados (tablas dinámicas, tableros de control) se usan principalmente en el MINCETUR, mientras que los emprendedores no reciben información oportuna, comprensible ni personalizada que les ayude a mejorar su oferta.
- **Barreras socioculturales y tecnológicas:** conectividad a Internet intermitente o nula en varias regiones (Loreto, Cajamarca, Ucayali), baja alfabetización digital y predominio de lenguas originarias (quechua, aimara, awajún) dificultan la adopción de herramientas digitales convencionales.

Esta situación impide que los más de 190 mil beneficiarios directos del turismo comunitario, 55 comunidades, más de 200 negocios y más de 1,600 emprendedores registrados, conviertan los datos recolectados en valor económico, y que el Estado diseñe políticas basadas en evidencia confiable y oportuna.

## Público objetivo

- **Usuario principal:** el emprendedor rural de las comunidades turísticas, con habilidades digitales limitadas, hablante de español y/o quechua, que depende de un dispositivo móvil de gama media-baja.
- **Usuario institucional:** los funcionarios de la Dirección de Innovación de la Oferta Turística (DIOT) del MINCETUR, que necesitan datos agregados y de calidad para la toma de decisiones de política pública.
- **Beneficiarios indirectos:** operadores turísticos y otros actores de la cadena de valor del turismo comunitario, que podrían beneficiarse de información de mercado agregada.

El diseño y las pruebas internas del equipo se orientan a los escenarios de **Luquina (Puno)** y **Misminay (Cusco)**, comunidades de predominio quechuahablante señaladas como piloto en el reto original, lo que permite pensar la app en condiciones reales de conectividad y de idioma.

## Solución propuesta

**Yupay Turismo** se diseña para abordar directamente las causas del problema identificado, a través de los siguientes componentes, todos ellos implementados y validados internamente por el equipo:

1. **Registro inclusivo y offline.** Un formulario de registro de visita con selección de nacionalidad, servicios consumidos (adaptados al tipo de emprendimiento: hospedaje, alimentación o artesanía) y cálculo automático de subtotales y descuentos. El registro se guarda de inmediato en una base de datos local (Room) sin requerir conexión a Internet.

2. **Doble vía de sincronización.** Para el escenario sin Internet, la aplicación ofrece sincronización dispositivo a dispositivo por red local (sockets TCP, descubrimiento NSD y emparejamiento por código QR). Para cuando hay conexión disponible, ofrece sincronización con un servidor en la nube propio, con autenticación de usuario y actualización en tiempo real.

3. **Devolución multimodal de insights.** Los datos registrados se transforman en un panel de control con gráficos (visitantes, ventas, horarios de mayor afluencia), un mapa pictográfico de procedencia de los turistas, y consejos para el emprendedor que pueden reproducirse en voz alta en quechua o español mediante un motor de síntesis de voz offline, pensado para reducir las barreras de alfabetización del público objetivo.

4. **Enfoque en la confianza y la privacidad.** La aplicación no recopila datos personales sensibles del turista más allá de su nacionalidad y gasto aproximado, y la autenticación del emprendedor (cuando decide vincular su cuenta a la nube) se gestiona mediante tokens de sesión, sin almacenar contraseñas en texto plano en el dispositivo.

> **Alcance respecto a la propuesta original:** el reto de MINCETUR sugería complementar los datos locales con tendencias turísticas globales descargadas de fuentes externas. Esa integración con una fuente de tendencias de mercado en tiempo real no llegó a implementarse dentro del alcance de este curso; los consejos para el emprendedor que sí se entregaron (incluido su audio) provienen de contenido predefinido por categoría de negocio, no de un servicio externo de tendencias. Este alcance se detalla en la sección 7 (Funcionalidades de la Aplicación).

Con esta solución, el equipo busca sentar las bases técnicas para que, en una fase posterior con despliegue real, cada emprendedor pueda percibir el valor de registrar sus datos turísticos, y para que el MINCETUR pueda disponer de información oportuna y de calidad que fortalezca la Estrategia Nacional de Turismo Comunitario.