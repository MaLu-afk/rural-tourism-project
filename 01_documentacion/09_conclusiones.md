# 9. Conclusiones y Trabajo Futuro

## Lecciones aprendidas

El desarrollo de **Yupay Turismo** dejó varias lecciones para el equipo, tanto a nivel técnico como de organización del trabajo.

**Diseñar para el escenario más restrictivo simplifica las decisiones posteriores.** Partir del requisito de funcionamiento offline obligó a tomar decisiones de arquitectura desde el inicio (persistencia local como fuente de verdad, sincronización como un proceso secundario y no bloqueante) que luego facilitaron incorporar tanto la sincronización P2P como la sincronización con la nube sin tener que rediseñar el flujo de datos.

**Dos motores de sincronización independientes son más simples de razonar que uno híbrido.** En lugar de construir un solo sistema que intentara cubrir el caso "sin Internet, con otro dispositivo cerca" y el caso "con Internet, contra un servidor" con la misma lógica, separarlos en `P2pSyncController` y `CloudSyncEngine`, ambos leyendo y escribiendo sobre la misma base de datos local, redujo la complejidad de cada uno por separado, a costa de tener dos piezas de infraestructura que mantener.

**La inyección de dependencias manual fue suficiente para el tamaño del proyecto, pero tiene un techo.** Prescindir de Hilt simplificó el arranque del proyecto y dio control explícito sobre componentes que debían sobrevivir a una `Activity` (como el motor P2P). Sin embargo, hacia el final del desarrollo el `ServiceLocator` concentró bastante responsabilidad; en un proyecto más grande o con más tiempo, valdría la pena evaluar nuevamente un framework de DI.

**Las pruebas automatizadas llegaron tarde respecto al desarrollo de funcionalidades.** El equipo priorizó construir y validar manualmente cada módulo antes de escribir pruebas formales. Esto permitió iterar rápido al inicio, pero significó que algunas pruebas unitarias y de interfaz se escribieron sobre código ya estable en lugar de guiar su diseño. Adoptar pruebas desde etapas más tempranas habría dado mayor confianza durante los cambios de arquitectura (por ejemplo, al introducir la migración de Room para el merge P2P).

**Mantener la documentación al día con el código requiere disciplina explícita.** Durante el desarrollo, partes de la documentación quedaron desactualizadas respecto al estado real del código (por ejemplo, una funcionalidad que pasó de "simulada" a completamente implementada sin que el documento técnico se actualizara en el mismo momento). La revisión final de cierre del proyecto sirvió, entre otras cosas, para contrastar cada afirmación de la documentación contra el código real y corregir esas discrepancias antes de la entrega.

## Mejoras futuras y posibilidad de escalar

A partir del estado actual del proyecto, el equipo identificó las siguientes líneas de trabajo futuro:

- **Integración con fuentes externas de tendencias turísticas**, tal como planteaba originalmente el reto de MINCETUR, para enriquecer los consejos al emprendedor más allá del contenido predefinido por categoría de negocio.
- **Traducción automática offline de contenido dinámico** (nombres de productos, textos ingresados por el usuario), complementando la traducción ya existente de la interfaz estática.
- **Ampliación del catálogo de voces**, incorporando aimara y awajún, las otras dos lenguas originarias mencionadas en el reto, sujeto a la disponibilidad de modelos de síntesis de voz de código abierto para esos idiomas.
- **Mayor cobertura de pruebas automatizadas**, extendiendo las pruebas de interfaz con Jetpack Compose a pantallas completas (no solo a componentes aislados) y agregando pruebas instrumentadas para los DAOs de `Product` y `AppSettings`, y para el motor de sincronización P2P.
- **Migración o evaluación de un framework de inyección de dependencias** si el proyecto creciera en número de módulos o de integrantes del equipo.
- **Despliegue piloto en las comunidades de Luquina y Misminay**, que fueron el referente de diseño durante el desarrollo, como siguiente paso natural para validar la aplicación con usuarios reales y obtener retroalimentación de campo.
- **Interoperabilidad con las plataformas del MINCETUR**, requisito del reto original que no se abordó en este ciclo del curso y que sería necesario para una eventual adopción institucional de la herramienta.

El proyecto, en su estado actual, cubre de forma sólida el núcleo offline-first solicitado por el reto, registro de visitas, generación de insights, accesibilidad multimodal y sincronización bajo dos modalidades distintas, y deja una base técnica clara sobre la cual continuar trabajando en las líneas señaladas.
