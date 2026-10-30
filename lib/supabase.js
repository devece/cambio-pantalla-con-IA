'use strict';
/*
 * Cliente mínimo de Supabase (REST/PostgREST) sin dependencias.
 * Solo se usa desde el servidor, con la service role key.
 * Si faltan las variables de entorno, `configured()` devuelve false y la API trabaja en modo demostración.
 */

function configured() {
  return !!(process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_ROLE_KEY);
}

async function rest(path, { method = 'GET', body, headers = {} } = {}) {
  if (!configured()) throw new Error('Supabase no está configurado');
  const url = `${process.env.SUPABASE_URL.replace(/\/$/, '')}/rest/v1/${path}`;
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

module.exports = { configured, rest };
