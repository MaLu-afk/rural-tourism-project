<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1a472a,40:2d6a4f,70:40916c,100:74c69d&height=230&section=header&text=🌿%20Yupay%20Turismo&fontSize=58&fontColor=ffffff&animation=fadeIn&fontAlignY=38&fontAlign=50&desc=Del%20cuaderno%20al%20insight%20·%20Offline-First%20Mobile%20App&descSize=19&descColor=d8f3dc&descAlignY=58&descAlign=50"/>

<br/>

![Estado](https://img.shields.io/badge/Estado-Primera%20Entrega-52b788?style=for-the-badge&logo=checkmarx&logoColor=white)
![Plataforma](https://img.shields.io/badge/Plataforma-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Figma](https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white)
![UPCH](https://img.shields.io/badge/UPCH-Ingeniería_Informática-003366?style=for-the-badge&logo=academia&logoColor=white)

<br/>

[![Prototipo Figma](https://img.shields.io/badge/🎨%20Prototipo-Figma-F24E1E?style=for-the-badge)](https://www.figma.com/proto/YI8sJDKSiGodFOckt13sLw/Project_Design---DSM?node-id=1-584&t=vN5iSGJfo9nIvDD8-1)
[![Repositorio](https://img.shields.io/badge/📁%20Código%20Fuente-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/MaLu-afk/rural-tourism-project)

</div>

---

## 📌 Descripción

**Yupay Turismo** es una aplicación móvil *offline-first* para comunidades rurales del Perú que reemplaza el registro manual de visitantes turísticos (cuaderno) por una solución digital accesible, sin necesidad de internet. Los datos se sincronizan automáticamente cuando hay red disponible y se transforman en **insights visuales y audios en quechua y español**, facilitando la toma de decisiones tanto para emprendedores como para el MINCETUR.

> Propuesta de solución al reto **"Del cuaderno al insight"** — MINCETUR · ProInnovate 2025

---

## 🗂️ Estructura del Repositorio

```
📁 rural-tourism-project/
│
├── 📁 01_documentacion/
│   ├── 📄 01_introduccion.md     → Objetivo y descripción del proyecto
│   ├── 📄 02_justificacion.md    → Problema, público objetivo y solución
│   ├── 📄 03_diseno.md           → Mockups, herramientas y flujo de pantallas
│   ├── 📄 04_tecnologia.md      → Stack técnico y librerías
│   ├── 📄 05_control_versiones.md     → Ramas, commits y flujo de trabajo
│   └── 📄 06_funcionalidad.md    → Demo y módulos implementados
│
├── 📁 02_app/
│   └── 📁 src/
│       ├── 📁 data/              → Room · Retrofit · DataStore
│       ├── 📁 domain/            → Casos de uso · Lógica de negocio
│       └── 📁 presentation/      → ViewModels · Jetpack Compose UI
│
├── 📁 figma/                     → Assets y exportaciones de diseño
├── 📄 .gitignore
├── 📄 README.md
└── 📄 build.gradle
```

---

## 👥 Equipo

<div align="center">

| Integrante | Rol | GitHub |
|:----------|:----|:------:|
| 👨‍💻 Antony Ivan Mendoza Villar | Desarrollador | [![GitHub](https://img.shields.io/badge/@Sh3ccid-181717?style=flat-square&logo=github)](https://github.com/Sh3ccid) |
| 👨‍💻 Edithson Ricardo Aybar Escobar | Desarrollador | [![GitHub](https://img.shields.io/badge/@Edithson1-181717?style=flat-square&logo=github)](https://github.com/Edithson1) |
| 👩‍💻 Leily Marlith Llanos Angeles | Desarrolladora | [![GitHub](https://img.shields.io/badge/@leilyllanos-181717?style=flat-square&logo=github)](https://github.com/LeilyDev) |
| 👨‍💻 Magno Ricardo Luque Mamani | Desarrollador | [![GitHub](https://img.shields.io/badge/@MaLu-afk-181717?style=flat-square&logo=github)](https://github.com/MaLu-afk) |

**Docentes:** Wilder Nina Choquehuayta · Percy Wilianson Lovon Ramos

</div>

---

## 💡 Recomendaciones

> Para quienes revisen o colaboren en este proyecto:

- 📖 Leer la documentación en `/docs/` antes de contribuir — cada archivo detalla una sección específica de la entrega
- 🌿 Seguir el flujo de ramas definido: `feature/` → `develop` → `main`
- 📝 Usar **Conventional Commits**: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`
- 🔄 No hacer push directo a `main` — siempre abrir un **Pull Request** desde `develop`
- 📱 Probar en dispositivos de **gama baja** (el público objetivo usa hardware básico)
- 🌐 Verificar que las funciones críticas operan **sin conexión a internet**

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:74c69d,40:40916c,70:2d6a4f,100:1a472a&height=130&section=footer&text=🌿%20Yupay%20Turismo%20·%20UPCH%202025&fontSize=20&fontColor=d8f3dc&animation=fadeIn&fontAlign=50&fontAlignY=55"/>

</div>
