# MaKaDiMi 1.2.0

## New
- **Natural voice for DJ MaKa**: the DJ can speak with Gemini neural voices, far more natural than the phone's. Turn it on by pasting a free Gemini key (aistudio.google.com) in Settings > AI > "DJ natural voice". The voice is generated while the previous song plays, so there is no wait. With no key, or if it fails, it uses the phone voice as before.

## Improved
- **Redesigned download notification**: shows the percentage and how many songs are queued, uses the app color and opens MaKaDiMi when tapped. Download-complete and download-failed notices have their own titles.
- The DJ's phone voice now uses the best available voices (including network ones) and speaks with a more human pace and pauses.

## Fixed
- The Android DJ no longer goes silent without telling you: if the AI or the voice fails, it says so on screen and greets you with a local line. It also waits up to 20 s for the song to start before speaking.
- The default Gemini model is now `gemini-3.5-flash`, since Google retired `gemini-2.0-flash`.
