# MaKaDiMi 1.1.8

## Mejorado
- **Playlists de Spotify**: el inicio de sesión con Spotify ahora prueba todos los secretos conocidos (del más nuevo al más viejo, más los incluidos en la app) y los actualiza cada hora, para seguir funcionando cuando Spotify los cambie.
- **Registro de errores**: si las playlists de Spotify no cargan, el motivo exacto queda en el log de la app.
- **Marca**: los nombres que sobraban en los textos de la app ahora dicen MaKaDiMi.

## Corregido
- **Pantalla de licencias de código abierto**: el archivo de licencias tenía JSON inválido (comillas sin escapar), por lo que la lista podía no cargar.
