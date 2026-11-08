'use strict';
/*
 * Contadores públicos de Tablero: solo dos totales (visitas y descargas), sin datos de nadie.
 * Se guardan en dos tablas de Supabase (`tablero_stats`) a las que solo se llega por dos funciones (`tablero_hit` y `tablero_totals`).
 * Sumar exige una clave privada del servidor (STATS_HIT_KEY), para que nadie desde fuera pueda inflar los números.
 *
 * Contar NUNCA puede romper una descarga ni una visita: todo tiene plazo corto y los errores se tragan. Sin variables de
 * entorno (pruebas, demostración) no hace nada.
 */
const TIMEOUT_MS = 1500;
const BOT = /bot|crawl|spider|slurp|facebookexternalhit|preview|headless|lighthouse|monitor|uptime|curl|wget|python-requests|go-http|node-fetch|axios/i;

function config() {
  const url = process.env.STATS_SUPABASE_URL;
  const anon = process.env.STATS_SUPABASE_ANON_KEY;
  const key = process.env.STATS_HIT_KEY;
  return url && anon && key ? { url: url.replace(/\/$/, ''), anon, key } : null;
}

/** ¿Parece un robot (o no trae identificación)? Esos no cuentan como visita ni descarga. */
function isBot(req) {
  const ua = String((req && req.headers && req.headers['user-agent']) || '');
  return !ua || BOT.test(ua);
}

async function rpc(cfg, name, body) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), TIMEOUT_MS);
  try {
    const r = await fetch(cfg.url + '/rest/v1/rpc/' + name, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', apikey: cfg.anon, Authorization: 'Bearer ' + cfg.anon },
      body: JSON.stringify(body),
      signal: ctl.signal,
    });
    if (!r.ok) return null;
    const text = await r.text();
    return text ? JSON.parse(text) : true;
  } catch (_) {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

const KINDS = ['visits', 'downloads', 'downloads_design', 'downloads_plain'];

/**
 * Suma 1 a un contador. «downloads_design» y «downloads_plain» (descarga con diseño y normal) también suman al total
 * «downloads»: lo hace la base en la misma operación. Devuelve true si se pudo.
 */
async function hit(kind) {
  const cfg = config();
  if (!cfg || !KINDS.includes(kind)) return false;
  return (await rpc(cfg, 'tablero_hit', { p_kind: kind, p_secret: cfg.key })) !== null;
}

/** { visits, downloads, downloadsDesign, downloadsPlain } o null si no se pudo leer. */
async function totals() {
  const cfg = config();
  if (!cfg) return null;
  const rows = await rpc(cfg, 'tablero_totals', {});
  if (!Array.isArray(rows)) return null;
  const out = { visits: 0, downloads: 0, downloadsDesign: 0, downloadsPlain: 0 };
  const key = { visits: 'visits', downloads: 'downloads', downloads_design: 'downloadsDesign', downloads_plain: 'downloadsPlain' };
  rows.forEach((r) => { if (key[r.kind]) out[key[r.kind]] = Number(r.total) || 0; });
  return out;
}

/**
 * Cuenta una descarga de la APK (los robots no cuentan). `withDesign`: se entregó con el diseño del cliente adentro; si no,
 * se entregó la APK normal. Nunca lanza error.
 */
async function countDownload(req, withDesign) {
  if (isBot(req)) return false;
  return hit(withDesign ? 'downloads_design' : 'downloads_plain');
}

module.exports = { hit, totals, countDownload, isBot, config };
