# Video de presentación de Tablero (54 s)

Hecho con HyperFrames (HTML + GSAP → MP4). La música se sintetizó desde cero con `musica/sintetizar.py` (120 BPM, La menor, 27 compases; cada compás = 2 s y cada escena cambia en un compás).
Las pantallas son capturas reales de la vista previa del sitio (`public/`).

| Tiempo | Escena | Texto en pantalla |
|---|---|---|
| 0–4 s | Gancho | «La pantalla de tu radio puede verse como tú quieras.» |
| 4–10 s | Presentación | «Tablero: el launcher para radios Android de auto. Para Chile, Colombia, Ecuador y México.» · 100% gratis · Sin cuentas |
| 10–18 s | Diseños | «9 temas visuales · 9 distribuciones» (carrusel de 8 combinaciones) |
| 18–24 s | Día y noche | «Día y noche, por la hora del sol» |
| 24–30 s | Mantención | «Que no se te pase nada»: aceite, revisión/permiso/SOAP, kilometraje automático, combustible |
| 30–36 s | Restricciones | «Tu restricción, donde estés»: Chile (restricción vehicular), Colombia (pico y placa), México (Hoy No Circula) y Ecuador (pico y placa), con el aviso real de la radio |
| 36–42 s | Scanner OBD | «Tu auto te habla»: RPM/temperatura/voltaje, km por litro, fallas en palabras simples |
| 42–48 s | Descarga | «Diséñala en el sitio. Descárgala lista.» · Gratis · Sin códigos · Sin datos tuyos |
| 48–54 s | Cierre | «Tablero. Tu radio. Tu pantalla.» |

## Regenerar
```bash
python3 musica/sintetizar.py proyecto/musica.wav && ffmpeg -y -i proyecto/musica.wav -b:a 192k proyecto/assets/musica.mp3
cd proyecto && npx hyperframes@0.9.30 render -q standard -o ../tablero-presentacion.mp4
```
