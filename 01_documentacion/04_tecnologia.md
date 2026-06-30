# 4. Tecnologías Utilizadas

La aplicación se construye íntegramente sobre el ecosistema nativo de Android, priorizando el rendimiento, la eficiencia energética y la compatibilidad con dispositivos de gama media-baja, condiciones centrales del contexto de las comunidades rurales al que está dirigida. A continuación se describe la pila tecnológica utilizada en el producto.

## Kotlin y Android Studio

- **Kotlin** como lenguaje único de desarrollo, por su concisión, seguridad frente a nulos y adopción oficial en el ecosistema Android.
- **Android Studio** como entorno de desarrollo integrado.
- **Jetpack Compose + Material Design 3** para una interfaz declarativa y reactiva, con soporte de layout vertical (barra de navegación inferior) y horizontal (riel de navegación lateral) para distintos tamaños de pantalla.
- **Navigation Compose** para la gestión del grafo de navegación (26 rutas) y el paso de argumentos entre pantallas.
- **Compilación:** `minSdk = 24`, `compileSdk = 36`, `targetSdk = 34`. Empaquetado limitado a la arquitectura `arm64-v8a` para reducir el tamaño del APK, dado el peso de las librerías nativas del motor de voz.

## Otras librerías y servicios integrados

### Persistencia y almacenamiento local

- **Room** (versión de esquema 13, con una migración manual aplicada) para la base de datos local SQLite, con cinco entidades: `Visit` (visitas registradas), `Product` (catálogo del negocio), `AppSettings` (configuración del emprendedor), `PendingOp` (cola de operaciones pendientes de sincronizar) y `TtsPreference` (voz activa por idioma). El acceso a estas tablas se centraliza en un `DataRepository` único.
- **DataStore (Preferences)** para el almacenamiento seguro de la sesión de usuario (tokens de autenticación), fuera de Room y sin persistir contraseñas.
- **kotlinx.serialization** para convertir estructuras complejas (listas de productos seleccionados, mapas de texto por idioma) hacia y desde Room, y para la (de)serialización de los mensajes JSON intercambiados con la API y entre dispositivos.

### Conectividad con la nube

- **OkHttp + kotlinx.serialization** como cliente HTTP tipado contra una API REST propia, desplegada en un servicio en la nube (Render). Se evaluó el uso de Retrofit, pero el equipo optó por un cliente más liviano construido directamente sobre OkHttp, suficiente para el número de endpoints de la API y con menos dependencias.
- **Autenticación completa contra la API:** registro e inicio de sesión por correo y contraseña, inicio de sesión con Google (Credential Manager), verificación de cuenta por código (OTP) y recuperación de contraseña. Los tokens de sesión se renuevan automáticamente mediante un interceptor (`TokenAuthenticator`).
- **WebSocket en tiempo real** (protocolo Phoenix, sobre Supabase Realtime) para recibir notificaciones de cambios remotos y disparar una sincronización incremental, con reconexión automática y reintento con backoff exponencial ante cortes de red.
- **Sincronización incremental con patrón outbox:** los cambios locales se encolan (`PendingOp`) y se suben al servidor cuando hay conexión; los cambios remotos se descargan de forma incremental a partir de la última marca de tiempo sincronizada.

### Sincronización entre dispositivos (offline, P2P)

La sincronización P2P opera dispositivo a dispositivo dentro de la misma red local, sin depender de un servidor en la nube, lo que la hace coherente con el enfoque offline-first del proyecto:

- **Sockets TCP** (`ServerSocket`/`Socket`) para el intercambio de mensajes serializados en JSON.
- **Network Service Discovery (NSD/mDNS)** para que los dispositivos se descubran automáticamente en la red local.
- **Emparejamiento por código QR:** un dispositivo actúa como servidor y muestra un código QR; el otro lo escanea para conectarse, usando ML Kit Barcode Scanning, Play Services Code Scanner y ZXing.
- Este motor de sincronización corre como un proceso independiente del ciclo de vida de la pantalla (`ServiceLocator` + `SyncForegroundService`), de modo que sigue funcionando aunque la app esté en segundo plano.

### Mapa y visualización geográfica

- **osmdroid (OpenStreetMap)** para el mapa de procedencias de los turistas, renderizado a partir de un GeoJSON local de países, sin necesidad de conexión a Internet.
- **Coil** para la carga eficiente de imágenes (por ejemplo, la foto de perfil del negocio).

### Visualización de datos

- **Vico**, librería de gráficos para Jetpack Compose, utilizada en el panel de insights (Home y Dashboard), encapsulada en un módulo propio (`VicoCharts.kt`) para aislar al resto de la app de la versión específica de la librería.
- El dashboard se organiza en cuatro pestañas temáticas (Resumen, Visitantes, Ventas, Tiempos), con un filtro de periodo compartido entre ellas.

### Síntesis de voz (multimodalidad)

- **Sherpa-ONNX** como motor único de síntesis de voz offline, en reemplazo del TTS nativo de Android. Soporta dos arquitecturas de modelo: Piper (requiere datos de fonemización) y MMS de Meta (modelo de un solo locutor, sin datos adicionales).
- El catálogo de voces incluye español, inglés y portugués (modelos Piper) y **quechua del Cusco-Collao** (modelo MMS convertido a formato ONNX), siendo esta última la única voz disponible para esa lengua en el ecosistema de Sherpa-ONNX al momento del desarrollo.
- **WorkManager** se utiliza específicamente para la descarga bajo demanda de los modelos de voz (con restricción de red y progreso observable), no para la sincronización de datos, que se maneja con corrutinas de Kotlin.
- **Apache Commons Compress** para descomprimir los paquetes `.tar.bz2` de los modelos de voz Piper.
- **MediaPlayer** y un escritor de archivos WAV propio para la reproducción del audio sintetizado.

### Notificaciones

- Sistema de notificaciones con dos canales: uno de eventos (visitas y cambios recibidos por sincronización, con sonido) y uno silencioso para la notificación persistente del servicio de sincronización en primer plano.

### Arquitectura y buenas prácticas

- **MVVM**, con ViewModels de Android (`MainViewModel`, `DashboardViewModel`, `SyncViewModel`, `TtsViewModel`) y estado expuesto mediante `StateFlow`.
- Organización en paquetes por responsabilidad: `data` (Room, API REST, repositorios), `sync` (P2P), `tts` (síntesis de voz), `notifications`, `service`, `ui` (componentes, pantallas, navegación, tema) y `utils`.
- **Inyección de dependencias manual** a través de un localizador de servicios (`ServiceLocator`), en lugar de un framework como Hilt. Esta fue una decisión deliberada del equipo para mantener el control explícito sobre el ciclo de vida de los componentes de sincronización, que necesitan sobrevivir más allá de una `Activity`.
- Validación de datos en los ViewModels y pantallas: campos obligatorios, cálculo de totales con descuentos y verificación de completitud antes de guardar un registro.

### Internacionalización

- Recursos `strings.xml` para **cuatro idiomas**: español (por defecto), inglés, portugués y quechua.
- Una utilidad propia (`UiTranslations`) para resolver textos dinámicos (no presentes en `strings.xml`) según el idioma seleccionado en tiempo de ejecución.

## Diferencias respecto a la propuesta tecnológica inicial

Para mantener la trazabilidad del proyecto, se documentan a continuación las decisiones donde la implementación final difirió de lo planteado en la primera entrega:

| Tecnología propuesta inicialmente | Estado final | Observación |
|---|---|---|
| Retrofit + OkHttp | Solo OkHttp | Se prescindió de Retrofit; el contrato de la API se tipó manualmente sobre OkHttp + kotlinx.serialization. |
| Hilt (inyección de dependencias) | No utilizado | Se optó por un `ServiceLocator` manual para controlar el ciclo de vida de los componentes de sincronización fuera de las Activities. |
| Clean Architecture (capa `domain` separada) | Arquitectura de dos capas | La lógica de negocio se ubicó en los ViewModels y repositorios (`data`), sin una capa `domain` independiente. |
| DataStore para toda la configuración | Uso mixto | La configuración del emprendedor se almacenó en Room (`AppSettings`); DataStore se reservó para la sesión de autenticación. |
| Idiomas español y quechua | Ampliado a 4 idiomas | Se sumó inglés y portugués a la interfaz. |
| Mapa de procedencias | Añadido (no estaba en la propuesta original) | Se incorporó un mapa offline con osmdroid como parte del panel de insights. |
| Sincronización solo P2P | Ampliada a P2P + nube | Se agregó, además de la sincronización P2P prevista originalmente, un backend propio con autenticación y sincronización en tiempo real, no contemplado en el planteamiento inicial del curso. |
| Recomendaciones con tendencias turísticas externas | No implementado | Los consejos para el emprendedor provienen de contenido predefinido por categoría de negocio, no de una fuente externa de tendencias (ver sección 2). |

Todas las librerías de terceros se incluyeron mediante repositorios oficiales de Maven Central o, en el caso de las librerías nativas del motor de voz, empaquetadas localmente dentro del proyecto.