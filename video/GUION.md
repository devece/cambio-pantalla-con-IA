# Video de presentación de Tablero (122,5 s)

Hecho con HyperFrames (HTML + GSAP → MP4). La música se sintetizó desde cero con `musica/sintetizar.py` (96 BPM, La menor, 49 compases; cada compás = 2,5 s y cada escena cambia en un compás).
Las pantallas son capturas reales de la vista previa del sitio (`public/`); las de pantalla vertical y el clip de la pantalla viva se sacan con `capturas/capturar.cjs`.

| Tiempo | Escena | Texto en pantalla |
|---|---|---|
| 0–5 s | Gancho | «La pantalla de tu radio puede verse como tú quieras.» |
| 5–12,5 s | Presentación | «Tablero: el launcher para radios Android de auto. Funciona en cualquier país.» · 100% gratis · Sin anuncios ni cuentas · Android 5+ |
| 12,5–22,5 s | Diseños | «9 temas visuales · 9 distribuciones» (carrusel de 8 combinaciones) |
| 22,5–30 s | Pantalla vertical | «También en pantalla vertical»: las 9 distribuciones se reordenan en radios de 9,7"; se detecta sola o se elige en Ajustes; la barra de abajo pasa a dos filas |
| 30–37,5 s | Día y noche | «Día y noche, por la hora del sol» |
| 37,5–45 s | Tu auto | «Tu auto, protagonista»: foto sin fondo, de noche con luces, saludo por voz, tu frase y tus colores |
| 45–55 s | Pantalla viva | «Tu pantalla cobra vida» (clip real de 10 s): bienvenida al encender, auto vivo (respira y un reflejo le cruza la carrocería) y carretera al manejar; se elige en Ajustes y se apaga sola si la radio va lenta |
| 55–62,5 s | Temporada | «Se viste de fiesta»: Fiestas Patrias, Halloween, Navidad y Año Nuevo; cambian solas según la fecha |
| 62,5–70 s | Al volante | «Todo a la vista mientras manejas»: mapa en vivo, música con carátula, Android Auto y CarPlay, velocidad y límite, radio FM, botones del volante, clima, tus apps |
| 70–77,5 s | Mantención | «Que no se te pase nada»: aceite, revisión/permiso/SOAP, kilometraje automático, combustible |
| 77,5–85 s | Restricciones | «Avisos de restricción por GPS»: Chile, Colombia, México y Ecuador, con el aviso real · «¿Otro país? Elige «Otro»: sin restricciones» |
| 85–92,5 s | Scanner OBD | «Tu auto te habla»: RPM/temperatura/voltaje, km por litro, fallas en palabras simples |
| 92,5–100 s | Reconoce tu scanner | «Tu scanner, reconocido al instante»: «Buscar scanner cerca» (sin pasar por los ajustes de Bluetooth de la radio), «Probar conexión» (voltaje, el auto responde, RPM, temperatura) y tolerante con los clones |
| 100–107,5 s | Ventajas | «Y sin letra chica»: gratis, sin anuncios, sin cuentas, datos en tu radio, sin root, se desinstala, Android 5+, horizontal y vertical, se actualiza desde la app, km o millas y 12/24 h, movimiento a tu gusto, diagnóstico de la radio hasta el CAN |
| 107,5–115 s | Descarga | «Diséñala en el sitio. Descárgala lista.» · Gratis · Sin códigos · Sin datos tuyos |
| 115–122,5 s | Cierre | «Tablero. Tu radio. Tu pantalla.» · Gratis · para radios Android de auto · en cualquier país |

La velocidad es 0,8× respecto al montaje original (`VELOCIDAD` en `proyecto/index.html` y `BPM` en `musica/sintetizar.py`).

## Regenerar
```bash
# 1) capturas nuevas (pantallas verticales y clip de la pantalla viva): ver el encabezado de capturas/capturar.cjs
node capturas/capturar.cjs
# 2) música (49 compases = 122,5 s)
python3 musica/sintetizar.py musica.wav && ffmpeg -y -i musica.wav -b:a 192k proyecto/assets/musica.mp3
# 3) video maestro (1920×1080) y la versión liviana de la portada del sitio (1280×720)
cd proyecto && npx hyperframes@0.9.30 render -q standard --video-frame-format png -o ../tablero-presentacion.mp4
ffmpeg -y -i ../tablero-presentacion.mp4 -vf scale=1280:720 -c:v libx264 -preset slow -crf 29 -c:a aac -b:a 128k -movflags +faststart ../../public/video/tablero.mp4
```
El clip `assets/movimiento.mp4` es un `<video>` directo en el escenario (HyperFrames no admite un `<video>` dentro de una escena con tiempo); si cambias la escena de la pantalla viva, el video se mueve junto con su marco (`#movM` y `#devM`).
