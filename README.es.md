<div align="center">

<img src="Assets/PhoneLab_icon_512x512.png" width="96" alt="PhoneLab icon">

# PhoneLab

**Cada sensor de hardware, estadística del sistema y zona térmica de tu teléfono — en vivo, en una sola pantalla.**

[![Android CI](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/DrummingBird1/PhoneLab)](https://github.com/DrummingBird1/PhoneLab/releases/latest)

**Leer en otro idioma:** [English](README.md) · [עברית](README.he.md) · Español · [العربية](README.ar.md)

[**⬇ Descargar el último APK**](https://github.com/DrummingBird1/PhoneLab/releases/latest) · [Sitio web](https://drummingbird1.github.io/PhoneLab/) · [Política de privacidad](https://drummingbird1.github.io/phonelab-privacy/)

</div>

---

## ¿Qué es PhoneLab?

PhoneLab es una app de Android de una sola pantalla para quien quiera saber exactamente qué está haciendo el hardware de su dispositivo en este momento — desarrolladores que prueban el comportamiento de sensores, entusiastas de la tecnología, jugadores vigilando la temperatura, o cualquiera con curiosidad por lo que hay dentro de su teléfono.

Sin anuncios. Sin cuenta. Sin servicios en segundo plano cuando no estás usando la app. La aplicación solo lee sensores y archivos del sistema a los que cualquier app de Android ya tiene acceso — **nunca se conecta a internet**. Todos los detalles en la [Política de privacidad](https://drummingbird1.github.io/phonelab-privacy/).

## Capturas de pantalla

<div align="center">
<img src="Assets/screenshots/PhoneLab_screenshot_1_sensors.png" width="200" alt="Pestaña Sensores">
<img src="Assets/screenshots/PhoneLab_screenshot_2_system.png" width="200" alt="Pestaña Sistema">
<img src="Assets/screenshots/PhoneLab_screenshot_3_hardware.png" width="200" alt="Pestaña Hardware">
<img src="Assets/screenshots/PhoneLab_screenshot_4_about.png" width="200" alt="Pestaña Acerca de">
<img src="Assets/screenshots/PhoneLab_screenshot_5_settings.png" width="200" alt="Pestaña Ajustes">
</div>

## Funciones

**📡 Pestaña Sensores** — lecturas en vivo de unos 29 sensores (acelerómetro, giroscopio, magnetómetro, gravedad, vectores de rotación, barómetro, luz, proximidad, humedad, temperatura ambiente, ritmo cardíaco, contador de pasos, detectores de inclinación y movimiento, y más), además de velocidad GPS y un medidor de nivel de sonido en vivo. La disponibilidad de sensores varía según el dispositivo — la app indica claramente qué no tiene tu hardware.

**⚙️ Pestaña Sistema** — modelo del dispositivo, versión de Android, CPU/RAM/almacenamiento, temperaturas de zonas térmicas (CPU/GPU/batería/carcasa y más) con alertas codificadas por color, un benchmark de rendimiento en 4 fases, grabación de sesión en CSV que sigue funcionando en segundo plano mediante un servicio en primer plano, y exportación de especificaciones a un archivo de texto.

**🔧 Pestaña Hardware** — frecuencia de CPU en vivo por núcleo, estadísticas detalladas de batería, capacidades de pantalla (frecuencia de actualización, HDR), e indicadores de capacidades de hardware (NFC, Bluetooth, número de cámaras, registro biométrico).

**🏠 Widget de pantalla de inicio y mosaico de ajustes rápidos** — consulta la temperatura de la CPU sin abrir la app.

**🔔 Alertas térmicas** — una notificación opcional cuando la temperatura de la CPU supera un umbral, con histéresis para que no te sature de avisos.

**🎨 Dos modos de visualización** — Textual (números en bruto, ideal para desarrolladores) o Visual (iconos, medidores, barras de progreso) — cambia con un toque.

**🌙 Temas claro y oscuro**, **🌐 4 idiomas** (inglés, hebreo, español, árabe, con soporte completo de RTL), **📐 unidades métricas/imperiales**.

## PhoneLab Web

Un panel de sensores complementario basado en el navegador vive en este repositorio, dentro de [`Web/`](Web/), y funciona en vivo en **[sensolab-web.vercel.app](https://sensolab-web.vercel.app)** — sin instalación. Refleja lo que la plataforma web expone en tu navegador/dispositivo actual: sensores de movimiento y orientación, GPS, luz ambiente, un test de velocidad de internet, y exportación a CSV/PNG de todo lo que lee. Todo se ejecuta en el cliente; no se envía nada a ningún sitio.

## Descargar

Consigue el último APK firmado en la **[página de Releases](https://github.com/DrummingBird1/PhoneLab/releases)** — cada versión detalla qué cambió en lenguaje sencillo e incluye un APK listo para instalar. Se planea la distribución en Google Play; mientras tanto, el APK es la forma más rápida de obtener la versión actual.

## Compilar desde el código fuente

Requisitos: **JDK 21**, Android SDK 35, y el Gradle wrapper (incluido — no hace falta instalar Gradle aparte).

```bash
cd App
./gradlew assembleDebug      # APK debug sin firmar → app/build/outputs/apk/debug/
./gradlew test                # pruebas unitarias JUnit
```

Una compilación release necesita una clave de firma: copia `App/key.properties.template` a `App/key.properties` y completa los datos de tu propio keystore, luego ejecuta `./gradlew bundleRelease` o `./gradlew assembleRelease`. Sin `key.properties`, las compilaciones release igual se generan — solo que sin firmar.

Consulta [CLAUDE.md](CLAUDE.md) para un recorrido completo de la arquitectura (fragments, clases auxiliares, modelo de permisos, detalles no evidentes) — es el mismo documento de orientación usado para el desarrollo asistido por IA en este repositorio, y también funciona como documentación técnica viva.

## Stack tecnológico

Java, Views clásicas de Android + Material Components (sin Compose), `ViewPager2` + `TabLayout`, `WorkManager` para comprobaciones térmicas en segundo plano, un `Service` en primer plano para la grabación CSV, `TileService` para el mosaico de ajustes rápidos, y `AppWidgetProvider` para el widget de pantalla de inicio. El panel web está hecho con Vite + TypeScript, sin framework.

## Estructura del proyecto

```
PhoneLab/
├── App/            el proyecto Gradle — ábrelo en Android Studio
├── Web/            el panel del navegador (Vite + TypeScript)
├── Assets/         textos de la ficha de la tienda, iconos, capturas, changelogs
├── Distribution/   salida de compilación local (ignorado por git)
└── Archive/        instantáneas congeladas de versiones anteriores
```

## Licencia

Por ahora no se otorga una licencia de código abierto — el código es público por transparencia, pero todos los derechos están reservados. Abre un issue si quieres hablar sobre su reutilización.
