# MaKaDiMi 1.2.0

## Nuevo
- **Voz natural para DJ MaKa**: el DJ puede hablar con voces neuronales de Gemini, mucho más naturales que la del teléfono. Se activa pegando una clave gratuita de Gemini (aistudio.google.com) en Ajustes > IA > "Voz natural del DJ". La voz se genera mientras suena la canción anterior, así que no hay esperas. Sin clave, o si falla, usa la voz del teléfono como antes.

## Mejorado
- **Notificación de descarga rediseñada**: muestra el porcentaje y cuántas canciones quedan en cola, usa el color de la app y abre MaKaDiMi al tocarla. Los avisos de descarga completada y fallida tienen título propio.
- La voz del teléfono del DJ usa ahora las voces de mayor calidad disponibles (incluidas las de red) y habla con un ritmo y unas pausas más humanos.

## Corregido
- El DJ de Android ya no se queda mudo sin avisar: si la IA o la voz fallan, lo dice en pantalla y saluda con una frase local. Además espera hasta 20 s a que la canción arranque antes de hablar.
- El modelo de Gemini por defecto pasa a `gemini-3.5-flash`, porque `gemini-2.0-flash` ya fue retirado por Google.
