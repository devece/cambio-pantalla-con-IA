# Video de presentación de Tablero (78 s)

Hecho con HyperFrames (HTML + GSAP → MP4). La música se sintetizó desde cero con `musica/sintetizar.py` (120 BPM, La menor, 39 compases; cada compás = 2 s y cada escena cambia en un compás).
Las pantallas son capturas reales de la vista previa del sitio (`public/`).

| Tiempo | Escena | Texto en pantalla |
|---|---|---|
| 0–4 s | Gancho | «La pantalla de tu radio puede verse como tú quieras.» |
| 4–10 s | Presentación | «Tablero: el launcher para radios Android de auto. Funciona en cualquier país.» · 100% gratis · Sin anuncios ni cuentas · Android 5+ |
| 10–18 s | Diseños | «9 temas visuales · 9 distribuciones» (carrusel de 8 combinaciones) |
| 18–24 s | Día y noche | «Día y noche, por la hora del sol» |
| 24–30 s | Tu auto | «Tu auto, protagonista»: foto sin fondo, de noche con luces, saludo por voz, tu frase y tus colores |
| 30–36 s | Temporada | «Se viste de fiesta»: Fiestas Patrias, Halloween, Navidad y Año Nuevo; cambian solas según la fecha |
| 36–42 s | Al volante | «Todo a la vista mientras manejas»: mapa en vivo, música con carátula, Android Auto y CarPlay, velocidad y límite, radio FM, botones del volante, clima, tus apps |
| 42–48 s | Mantención | «Que no se te pase nada»: aceite, revisión/permiso/SOAP, kilometraje automático, combustible |
| 48–54 s | Restricciones | «Avisos de restricción por GPS»: Chile, Colombia, México y Ecuador, con el aviso real · «¿Otro país? Todo lo demás funciona igual» |
| 54–60 s | Scanner OBD | «Tu auto te habla»: RPM/temperatura/voltaje, km por litro, fallas en palabras simples |
| 60–66 s | Ventajas | «Y sin letra chica»: gratis, sin anuncios, sin cuentas, datos en tu radio, sin root, se desinstala, Android 5+, horizontal y vertical, se actualiza desde la app, km o millas y 12/24 h |
| 66–72 s | Descarga | «Diséñala en el sitio. Descárgala lista.» · Gratis · Sin códigos · Sin datos tuyos |
| 72–78 s | Cierre | «Tablero. Tu radio. Tu pantalla.» · Gratis · para radios Android de auto · en cualquier país |

## Regenerar
```bash
python3 musica/sintetizar.py musica.wav && ffmpeg -y -i musica.wav -b:a 192k proyecto/assets/musica.mp3
cd proyecto && npx hyperframes@0.9.30 render -q standard -o ../tablero-presentacion.mp4
```
