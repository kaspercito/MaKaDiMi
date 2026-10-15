# MaKaDiMi 1.1.8

## Improved
- **Spotify playlists**: the Spotify login now tries every known secret (newest first, plus ones bundled in the app) and refreshes the list every hour, so it keeps working when Spotify rotates them.
- **Error log**: when Spotify playlists fail to load, the exact reason is written to the app log.
- **Branding**: leftover names in the app's texts now say MaKaDiMi.

## Fixed
- **Open source licenses screen**: the licenses file had invalid JSON (unescaped quotes), so the list could fail to load.
