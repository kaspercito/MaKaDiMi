# MaKaDiMi 1.2.2

## New
- **Futuristic neon-green look**: new palette (neon green, cyan and lime) on a green-black base, no more violet or pink. Light mode becomes mint-white with a deep green accent.
- **Customizable background** in Settings > Theme > Background: HUD grid, Aurora, Dot matrix, Pure black or **your own image** from the gallery. You can also pick the glow color and intensity.
- **New icon**: a hexagon with a neon "M", plus a monochrome version for Android 13+ themed icons and the splash screen.

## Improved (the phone no longer runs as hot)
- The player no longer polls the position 20 times a second all the time: it is slower while music plays (including in the background while gaming) and only returns to fine timing as a crossfade approaches.
- The crossfade rainbow and the "Crossfading" shimmer no longer run at 60 fps all the time: they only exist while a crossfade is happening.
- The animated lyrics gradient no longer recomposes the whole screen every frame.
- The app background is static and computed once, not every frame.
- **Long-press a song** in any list to open its menu (download, queue, playlist...) without opening the song.
