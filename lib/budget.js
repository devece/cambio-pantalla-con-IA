'use strict';
/*
 * Tope diario de llamadas a la IA (análisis de la captura y detección de faros), para que un uso abusivo no genere gasto.
 *
 * Falla cerrada: sin la variable AI_DAILY_MAX (un número entero mayor que cero) NO se llama a la IA, aunque exista ANTHROPIC_API_KEY.
 * Así activar la IA exige decidir cuántas llamadas por día se aceptan.
 *
 * El contador vive en la memoria de cada instancia de la función, por lo que es un freno de mejor esfuerzo y no un tope global exacto:
 * el tope real lo ponen el límite de gasto de la cuenta de Anthropic y la regla del Firewall de Vercel (ver docs/SEGURIDAD.md).
 */

const day = { key: '', used: 0 };

/** El tope diario configurado, o 0 si no hay (IA apagada). */
function dailyMax() {
  const n = Number(process.env.AI_DAILY_MAX);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

/** Pide una llamada. { ok: true } o { ok: false, reason: 'off' | 'cap' }. */
function take(now = new Date()) {
  const max = dailyMax();
  if (!max) return { ok: false, reason: 'off' };
  const key = now.toISOString().slice(0, 10);
  if (day.key !== key) { day.key = key; day.used = 0; }
  if (day.used >= max) return { ok: false, reason: 'cap' };
  day.used += 1;
  return { ok: true };
}

/** Solo para las pruebas. */
function _reset() { day.key = ''; day.used = 0; }

module.exports = { take, dailyMax, _reset };
