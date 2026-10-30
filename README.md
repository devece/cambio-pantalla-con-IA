# Tablero

Configurador web para un launcher de radios Android de auto, pensado para Chile (precios en CLP, teléfono +56 9, restricción vehicular, revisión técnica, permiso de circulación, SOAP).

El cliente elige radio, auto, diseño y funciones; ve su pantalla funcionando en vivo; paga; y recibe un código para activar el launcher en su radio.

> **Estado: prototipo.** La vista previa, el configurador y la validación de pedidos funcionan. **Faltan** el cobro real, el launcher Android nativo y los textos legales. Ver `docs/ROADMAP.md`.

## Qué incluye

- Sitio estático (HTML/CSS/JS sin build) en `public/`.
- Vista previa de la radio (1024×600) con 8 temas visuales (día y noche), 4 distribuciones, 5 pantallas, fondos, autos generados en SVG y decoración de temporada (Fiestas Patrias, Halloween, Navidad, Año Nuevo).
- Planes: **Esencial** $9.990 y **Completo** $14.990 CLP (valores de ejemplo, ajústalos en `public/shared/catalog.js`).
- Funciones de API (Vercel) en `api/`: `checkout` (valida y cotiza; el precio siempre se recalcula en el servidor) y `license` (activa un código vinculado a una sola radio).
- Esquema de base de datos Supabase en `supabase/schema.sql` (RLS activado, sin políticas públicas).

## Cómo correrlo

```bash
npm test          # 13 pruebas de cotización y validación
npm run dev       # http://localhost:3000
```

Sin variables de entorno funciona en **modo demostración** (el pedido se confirma pero no se guarda ni se cobra).

## Variables de entorno

Copia `.env.example` a `.env`:

| Variable | Para qué |
|---|---|
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | Guardar pedidos y licencias |
| `LICENSE_PEPPER` | Secreto largo y aleatorio para vincular licencias a una radio |
| `PAYMENT_PROVIDER` | Reservado para el medio de pago (aún no implementado) |

## Publicar en Vercel

1. Sube el repositorio a GitHub e impórtalo en Vercel (la configuración está en `vercel.json`).
2. Crea el proyecto en Supabase y ejecuta `supabase/schema.sql`.
3. Carga las variables de entorno en Vercel.
4. Cuando todo esté listo, cambia `TB.site.prototype` a `false` en `public/js/site.js` y completa `supportEmail` y `whatsapp`.

## Notas legales

- Este proyecto es de código y diseño propios. El sitio que lo inspiró no tiene licencia en su repositorio, así que **no se copió** su código, textos, marca ni imágenes. Mantén esa separación.
- Los textos de términos, privacidad y devoluciones son marcadores: revísalos con un abogado antes de vender.
- `package.json` está como `UNLICENSED`; elige la licencia que corresponda antes de hacer público el repo.
