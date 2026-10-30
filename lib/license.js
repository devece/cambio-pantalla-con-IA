'use strict';
/*
 * Códigos de licencia: TBL-XXXX-XXXX-XXXX
 * Alfabeto sin caracteres ambiguos (sin 0/O, 1/I/L).
 */
const crypto = require('node:crypto');

const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const FORMAT = /^TBL-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/;

function group() {
  let out = '';
  for (let i = 0; i < 4; i++) out += ALPHABET[crypto.randomInt(ALPHABET.length)];
  return out;
}

function generateCode() {
  return `TBL-${group()}-${group()}-${group()}`;
}

/** Mayúsculas, sin espacios; acepta que el cliente escriba sin guiones. */
function normalizeCode(raw) {
  const s = String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (!s.startsWith('TBL') || s.length !== 15) return null;
  const body = s.slice(3);
  const code = `TBL-${body.slice(0, 4)}-${body.slice(4, 8)}-${body.slice(8, 12)}`;
  return FORMAT.test(code) ? code : null;
}

/** Hash para guardar el identificador de la radio sin dejarlo en claro. */
function hashDeviceId(deviceId, pepper) {
  return crypto.createHmac('sha256', String(pepper || '')).update(String(deviceId)).digest('hex');
}

module.exports = { generateCode, normalizeCode, hashDeviceId, FORMAT };
