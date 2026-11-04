'use strict';
/*
 * Cliente mínimo de Supabase (REST/PostgREST) sin dependencias.
 * Solo se usa desde el servidor, con la service role key.
 * Si faltan las variables de entorno, `configured()` devuelve false y la API trabaja en modo demostración.
 */

/*
 * El resto del código habla de `orders` y `licenses`. En la base de datos se llaman `tablero_orders` y `tablero_licenses`
 * para convivir con otras apps en el mismo proyecto de Supabase sin chocar con sus tablas.
 */
const TABLES = {
  orders: process.env.TB_ORDERS_TABLE || 'tablero_orders',
  licenses: process.env.TB_LICENSES_TABLE || 'tablero_licenses',
};
const tablePath = (path) => String(path).replace(/^(orders|licenses)(?=$|[?/])/, (m) => TABLES[m]);

function configured() {
  return !!(process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_ROLE_KEY);
}

async function rest(path, { method = 'GET', body, headers = {} } = {}) {
  if (!configured()) throw new Error('Supabase no está configurado');
  const url = `${process.env.SUPABASE_URL.replace(/\/$/, '')}/rest/v1/${tablePath(path)}`;
  const res = await fetch(url, {
    method,
    headers: {
      apikey: process.env.SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${process.env.SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = text;
  }
  if (!res.ok) {
    const err = new Error(`Supabase ${res.status}`);
    err.status = res.status;
    err.detail = data;
    throw err;
  }
  return data;
}

/** Sube un archivo a Supabase Storage (bucket privado). Solo desde el servidor, con la service role key. */
async function storageUpload(bucket, path, buffer, contentType) {
  if (!configured()) throw new Error('Supabase no está configurado');
  const url = `${process.env.SUPABASE_URL.replace(/\/$/, '')}/storage/v1/object/${bucket}/${path}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      apikey: process.env.SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${process.env.SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': contentType,
      'x-upsert': 'true',
    },
    body: buffer,
  });
  if (!res.ok) {
    const err = new Error(`Supabase Storage ${res.status}`);
    err.status = res.status;
    err.detail = await res.text().catch(() => '');
    throw err;
  }
  return path;
}

/** Enlace temporal (firmado) para leer un archivo de un bucket privado. Devuelve la URL completa. */
async function storageSignedUrl(bucket, path, expiresIn = 3600) {
  if (!configured()) throw new Error('Supabase no está configurado');
  const base = process.env.SUPABASE_URL.replace(/\/$/, '');
  const res = await fetch(`${base}/storage/v1/object/sign/${bucket}/${path}`, {
    method: 'POST',
    headers: {
      apikey: process.env.SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${process.env.SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ expiresIn }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || !data.signedURL) {
    const err = new Error(`Supabase Storage firma ${res.status}`);
    err.status = res.status;
    throw err;
  }
  const rel = String(data.signedURL);
  return rel.startsWith('http') ? rel : `${base}/storage/v1${rel.startsWith('/') ? '' : '/'}${rel}`;
}

module.exports = { configured, rest, tablePath, storageUpload, storageSignedUrl, PHOTO_BUCKET: process.env.TB_PHOTO_BUCKET || 'tablero-fotos' };
