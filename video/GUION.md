# Video de presentación de Tablero (97,5 s)

Hecho con HyperFrames (HTML + GSAP → MP4). La música se sintetizó desde cero con `musica/sintetizar.py` (96 BPM, La menor, 39 compases; cada compás = 2,5 s y cada escena cambia en un compás).
Las pantallas son capturas reales de la vista previa del sitio (`public/`).

| Tiempo | Escena | Texto en pantalla |
|---|---|---|
| 0–5 s | Gancho | «La pantalla de tu radio puede verse como tú quieras.» |
| 5–12,5 s | Presentación | «Tablero: el launcher para radios Android de auto. Funciona en cualquier país.» · 100% gratis · Sin anuncios ni cuentas · Android 5+ |
| 12,5–22,5 s | Diseños | «9 temas visuales · 9 distribuciones» (carrusel de 8 combinaciones) |
| 22,5–30 s | Día y noche | «Día y noche, por la hora del sol» |
| 30–37,5 s | Tu auto | «Tu auto, protagonista»: foto sin fondo, de noche con luces, saludo por voz, tu frase y tus colores |
| 37,5–45 s | Temporada | «Se viste de fiesta»: Fiestas Patrias, Halloween, Navidad y Año Nuevo; cambian solas según la fecha |
| 45–52,5 s | Al volante | «Todo a la vista mientras manejas»: mapa en vivo, música con carátula, Android Auto y CarPlay, velocidad y límite, radio FM, botones del volante, clima, tus apps |
| 52,5–60 s | Mantención | «Que no se te pase nada»: aceite, revisión/permiso/SOAP, kilometraje automático, combustible |
| 60–67,5 s | Restricciones | «Avisos de restricción por GPS»: Chile, Colombia, México y Ecuador, con el aviso real · «¿Otro país? Todo lo demás funciona igual» |
| 67,5–75 s | Scanner OBD | «Tu auto te habla»: RPM/temperatura/voltaje, km por litro, fallas en palabras simples |
| 75–82,5 s | Ventajas | «Y sin letra chica»: gratis, sin anuncios, sin cuentas, datos en tu radio, sin root, se desinstala, Android 5+, horizontal y vertical, se actualiza desde la app, km o millas y 12/24 h |
| 82,5–90 s | Descarga | «Diséñala en el sitio. Descárgala lista.» · Gratis · Sin códigos · Sin datos tuyos |
| 90–97,5 s | Cierre | «Tablero. Tu radio. Tu pantalla.» · Gratis · para radios Android de auto · en cualquier país |

La velocidad es 0,8× respecto al montaje original (`VELOCIDAD` en `proyecto/index.html` y `BPM` en `musica/sintetizar.py`).

## Regenerar
```bash
python3 musica/sintetizar.py musica.wav && ffmpeg -y -i musica.wav -b:a 192k proyecto/assets/musica.mp3
cd proyecto && npx hyperframes@0.9.30 render -q standard -o ../tablero-presentacion.mp4
```
