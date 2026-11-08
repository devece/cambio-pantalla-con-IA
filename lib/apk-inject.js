'use strict';
/*
 * Agrega un dato propio al «bloque de firmas» de una APK sin invalidar su firma (la misma técnica que usan los
 * instaladores por canal). El bloque de firmas de Android (esquema v2 o superior) guarda pares ID-valor; el instalador
 * ignora los ID que no conoce, y el resumen firmado no incluye el bloque entero: solo el contenido del zip, el directorio
 * central y el final del zip (donde la posición del directorio central se cuenta desde el inicio del bloque).
 *
 * Diseño del archivo:  [entradas del zip] [bloque de firmas] [directorio central] [fin del zip]
 * Bloque de firmas:    [tamaño u64] [pares ...] [tamaño u64] ["APK Sig Block 42"]
 * Cada par:            [largo u64 = 4 + valor] [id u32] [valor]
 *
 * Lo lee la app con `ApkBlock.kt` (android/core). Si la APK no tiene bloque de firmas, no se toca: `injectPair` lanza un error.
 */

const MAGIC = Buffer.from('APK Sig Block 42', 'latin1');
const EOCD_SIG = 0x06054b50;
const MAX_BLOCK = 16 * 1024 * 1024;

/** Identificadores de Tablero dentro del bloque: el diseño («TBL1») y la foto del auto de día («TBL2») y de noche («TBL3»). */
const DESIGN_ID = 0x54424c31;
const PHOTO_DAY_ID = 0x54424c32;
const PHOTO_NIGHT_ID = 0x54424c33;

function locate(apk) {
  if (!Buffer.isBuffer(apk) || apk.length < 64) throw new Error('No es una APK.');
  const lowest = Math.max(0, apk.length - 22 - 0xffff);
  let eocd = -1;
  for (let i = apk.length - 22; i >= lowest; i--) {
    if (apk.readUInt32LE(i) === EOCD_SIG && i + 22 + apk.readUInt16LE(i + 20) === apk.length) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('No encuentro el final del zip.');
  const cdOffset = apk.readUInt32LE(eocd + 16);
  if (cdOffset < 32 || cdOffset > eocd) throw new Error('Directorio central inválido.');
  if (!apk.subarray(cdOffset - 16, cdOffset).equals(MAGIC)) throw new Error('La APK no tiene bloque de firmas (v2 o superior).');
  const size = Number(apk.readBigUInt64LE(cdOffset - 24));
  const blockStart = cdOffset - size - 8;
  if (size < 24 || size > MAX_BLOCK || blockStart < 0) throw new Error('Bloque de firmas inválido.');
  if (Number(apk.readBigUInt64LE(blockStart)) !== size) throw new Error('Bloque de firmas dañado.');
  return { eocd, cdOffset, blockStart, size };
}

/** Los pares del bloque: [{ id, value }]. */
function pairs(apk, loc) {
  const out = [];
  let p = loc.blockStart + 8;
  const end = loc.cdOffset - 24;
  while (p < end) {
    if (p + 12 > end) throw new Error('Par inválido en el bloque de firmas.');
    const len = Number(apk.readBigUInt64LE(p));
    if (len < 4 || p + 8 + len > end) throw new Error('Par inválido en el bloque de firmas.');
    out.push({ id: apk.readUInt32LE(p + 8), value: apk.subarray(p + 12, p + 8 + len) });
    p += 8 + len;
  }
  return out;
}

function pair(id, value) {
  const head = Buffer.alloc(12);
  head.writeBigUInt64LE(BigInt(4 + value.length), 0);
  head.writeUInt32LE(id >>> 0, 8);
  return Buffer.concat([head, value]);
}

/** El valor guardado con ese id, o null. */
function readPair(apk, id = DESIGN_ID) {
  const hit = pairs(apk, locate(apk)).find((x) => x.id === (id >>> 0));
  return hit ? Buffer.from(hit.value) : null;
}

/** Devuelve una APK nueva con el par agregado (reemplaza uno anterior con el mismo id). La original no se toca. */
function injectPair(apk, id, value) {
  const loc = locate(apk);
  const keep = pairs(apk, loc).filter((x) => x.id !== (id >>> 0));
  const body = Buffer.concat([...keep.map((x) => pair(x.id, x.value)), pair(id, value)]);
  const size = body.length + 8 + 16; // pares + segundo tamaño + magia
  const head = Buffer.alloc(8);
  head.writeBigUInt64LE(BigInt(size));
  const tail = Buffer.from(apk.subarray(loc.cdOffset)); // directorio central + final del zip
  const newCdOffset = loc.blockStart + 8 + body.length + 8 + 16;
  tail.writeUInt32LE(newCdOffset, loc.eocd - loc.cdOffset + 16);
  return Buffer.concat([apk.subarray(0, loc.blockStart), head, body, head, MAGIC, tail]);
}

module.exports = { DESIGN_ID, PHOTO_DAY_ID, PHOTO_NIGHT_ID, locate, pairs, readPair, injectPair };
