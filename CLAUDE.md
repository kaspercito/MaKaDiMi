# MaKaDiMi — notas para asistentes de IA

- Responde siempre en **español**.
- MaKaDiMi es un fork personal de SimpMusic (GPL-3.0, © Tuan Minh Nguyen Duc / maxrave-dev). Conserva los avisos de licencia y atribución.
- Android: `applicationId = com.makadimi.app`; los paquetes Kotlin son `com.kaspercito.*` (renombrados desde el original; los `namespace` de Gradle usan lo mismo).
- Enlaces del "dueño": un único punto en `composeApp/src/commonMain/kotlin/com/kaspercito/simpmusic/utils/BrandLinks.kt`
  (y `scripts/set-github-user.sh` para fijar el usuario de GitHub en todo el proyecto).
- `core/` es código propio dentro del repo (ya no es un submódulo de git).
- No cambies: la URL de `mpvNativesRepo`, la de `KuromojiDictionary` ni la config remota (`remote-config.json`): son archivos que sólo existen en el repo original y la app los necesita.
- Estructura: `composeApp` (UI compartida), `androidApp`, `desktopApp`, `core/*` (dominio, datos, servicios).
- Compilar: `./gradlew :androidApp:assembleDebug` (JDK 21 + Android SDK).
