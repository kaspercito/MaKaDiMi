<div align="center">
  <img src="asset/makadimi_logo.png" width="160" alt="MaKaDiMi logo">
  <h1>MaKaDiMi</h1>
  Una app de música para todos: YouTube Music sin anuncios, con letras sincronizadas, ecualizador,<br>
  estadísticas de escucha y mucho más. Para Android y Escritorio, hecha con Compose Multiplatform.
</div>

> **MaKaDiMi** es un proyecto personal basado en [SimpMusic](https://github.com/maxrave-dev/SimpMusic)
> de Tuan Minh Nguyen Duc (maxrave-dev), publicado bajo la licencia **GPL-3.0**. Se conservan los avisos de
> copyright y la atribución al autor original, como exige la licencia (ver [LICENSE](LICENSE)).

## Características ✨
- Reproduce música de YouTube Music / YouTube gratis, sin anuncios y en segundo plano
- Tres estilos de reproductor: Clásico, Material 3 Expressive y estilo Apple Music con letras palabra por palabra
- Ecualizador de 10 bandas con presets y perfiles AutoEq, más efectos de Delay y Reverb
- Letras sincronizadas (SimpMusic Lyrics, LRCLIB, Spotify) con romanización en 12 idiomas
- Estadísticas de escucha locales, "Wrapped" anual y recibo de escucha para compartir
- Búsqueda en todo YouTube, listas, álbumes, podcasts, modos y géneros
- SponsorBlock, ReturnYouTubeDislike, Canvas de Spotify, sugerencias con IA
- Reproducción sin conexión (descargas), widgets, Android Auto
- Versión de Escritorio (Windows, macOS, Linux)

## Compilar la app Android
```bash
git clone https://github.com/kaspercito/MaKaDiMi.git
cd MaKaDiMi
./gradlew :androidApp:assembleDebug          # APK de prueba (id: com.makadimi.app.dev)
```
Requisitos: JDK 21 y Android SDK (compileSdk 37). Para un APK firmado de release usa `./build_and_sign_apk.sh --release`
(necesitas tu propio keystore; **nunca** lo subas al repositorio).

## Personalización
- **Tu usuario de GitHub**: ejecuta `./scripts/set-github-user.sh TU_USUARIO` y se actualizan todos los enlaces
  (Acerca de, Ajustes, actualizaciones, Discord Rich Presence, plantillas de issues). El punto único en código es
  [`BrandLinks.kt`](composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/utils/BrandLinks.kt).
- **Actualizaciones**: la app consulta `https://api.github.com/repos/<usuario>/MaKaDiMi/releases/latest`;
  publica tus APK como *Releases* con una etiqueta tipo `v1.0.0`.
- **Logo**: `asset/makadimi_logo.png` y los iconos de `androidApp/src/main/res`.

## Servicios de terceros
MaKaDiMi sigue usando servicios públicos del proyecto original y de terceros (no son tuyos y pueden cambiar):
SimpMusic Lyrics (letras), SimpMusic Charts, LRCLIB, SponsorBlock, ReturnYouTubeDislike y el archivo de configuración
remota del proyecto original (credenciales públicas de TIDAL).

## Privacidad
La versión FOSS no incluye rastreadores ni envía datos a terceros. Si activas "Send back to Google" estando conectado
a YouTube, se envía el historial de escucha a Google para mejorar las recomendaciones.

## Preguntas frecuentes
**¿Por qué las letras a veces no coinciden?** Las letras vienen de varias fuentes y se emparejan por título y duración;
a veces una canción recibe una letra equivocada.

## Legal Disclaimer & Terms of Use

### 1. 100% Free, Open-Source & Strictly Non-Commercial
MaKaDiMi is a fully open-source project (FOSS) created purely for educational purposes and personal use. **We do not sell this application, nor do we monetize it in any way.** There are no advertisements, no premium features, no subscriptions, and no hidden fees within the app. This project has absolutely no commercial value or financial intent. 

### 2. A Custom Browser with Content Filtering
MaKaDiMi acts strictly as a specialized, third-party web browser and client. It simply parses the publicly available website content and APIs of YouTube and YouTube Music, rendering them in a custom user interface. The ad-free experience it provides is fundamentally no different from using a standard web browser (like Chrome, Firefox, or Brave) equipped with a common ad-blocking extension (such as uBlock Origin). 

### 3. Support Content Creators
We deeply respect the hard work of artists, musicians, and content creators. **We strongly encourage all users to subscribe to [YouTube Premium](https://www.youtube.com/premium).** Purchasing a Premium subscription is the best way to financially support the creators you listen to and ensure the continued growth of the platform. MaKaDiMi is built as a proof-of-concept for developers and enthusiasts, not to harm creators' revenues.

### 4. No Hosting of Copyrighted Material
We do not host, upload, distribute, or store any audio, video, or copyrighted media files on our own servers. All content accessed through this application is stored entirely on Google's/YouTube's servers and remains the property of their respective copyright owners. The app merely acts as a conduit to stream publicly accessible links.

### 5. User Responsibility & Legal Contact
The software is provided "AS IS", without warranty of any kind. The developers of MaKaDiMi do not encourage or condone piracy. Users are solely responsible for ensuring their usage of this app complies with their local copyright laws and the Terms of Service of the platforms they access. 

Because we do not host any media files, we cannot process DMCA takedown requests for audio or video content. However, if you represent a copyright holder or have legal concerns regarding the open-source code itself, please contact us by opening a GitHub issue in this repository


## Créditos y licencia
- Proyecto original: [SimpMusic](https://github.com/maxrave-dev/SimpMusic) © 2023 Tuan Minh Nguyen Duc (maxrave-dev), GPL-3.0.
- MaKaDiMi © MaKaDiMi, distribuido bajo **GPL-3.0**: si distribuyes la app, debes ofrecer también su código fuente.
