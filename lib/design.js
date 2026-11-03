'use strict';
/*
 * El diseño que el cliente armó en el sitio, listo para que la app de la radio lo descargue al activar su código:
 * la configuración (sin datos de contacto) y enlaces temporales a su foto, que vive en un bucket privado.
 */
const db = require('./supabase.js');

const PART = ['radio', 'car', 'style', 'features'];
const URL_SECONDS = 3600;

async function sign(path) {
  if (!path) return null;
  try {
    return await db.storageSignedUrl(db.PHOTO_BUCKET, path, URL_SECONDS);
  } catch (_) {
    return null; // sin foto el diseño igual sirve
  }
}

/** `config` es la columna config del pedido. Devuelve null si el código se creó a mano (sin diseño). */
async function buildDesign(config) {
  if (!config || typeof config !== 'object' || config.manual) return null;
  const out = { config: {}, photo: null };
  PART.forEach((k) => { if (config[k] && typeof config[k] === 'object') out.config[k] = config[k]; });
  if (!Object.keys(out.config).length) return null;
  const p = config.photo;
  if (p && p.day) {
    const [day, night] = await Promise.all([sign(p.day), sign(p.night)]);
    if (day) out.photo = { day, night, cut: p.cut === true, lights: Array.isArray(p.lights) ? p.lights : [] };
  }
  return out;
}

module.exports = { buildDesign };
