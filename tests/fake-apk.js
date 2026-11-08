'use strict';
// Una «APK» mínima para las pruebas: un archivo sin comprimir, un bloque de firmas con un par ajeno, directorio central y final del zip.
const MAGIC = Buffer.from('APK Sig Block 42', 'latin1');
const u16 = (n) => { const b = Buffer.alloc(2); b.writeUInt16LE(n); return b; };
const u32 = (n) => { const b = Buffer.alloc(4); b.writeUInt32LE(n >>> 0); return b; };
const u64 = (n) => { const b = Buffer.alloc(8); b.writeBigUInt64LE(BigInt(n)); return b; };

function fakeApk(extraPairs = [{ id: 0x7109871a, value: Buffer.from('firma-falsa') }]) {
  const name = Buffer.from('a.txt');
  const data = Buffer.from('hola');
  const local = Buffer.concat([u32(0x04034b50), u16(10), u16(0), u16(0), u16(0), u16(0), u32(0), u32(data.length), u32(data.length), u16(name.length), u16(0), name, data]);
  const body = Buffer.concat(extraPairs.map((p) => Buffer.concat([u64(4 + p.value.length), u32(p.id), p.value])));
  const size = body.length + 8 + 16;
  const block = Buffer.concat([u64(size), body, u64(size), MAGIC]);
  const cd = Buffer.concat([u32(0x02014b50), u16(20), u16(10), u16(0), u16(0), u16(0), u16(0), u32(0), u32(data.length), u32(data.length), u16(name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(0), name]);
  const cdOffset = local.length + block.length;
  const eocd = Buffer.concat([u32(0x06054b50), u16(0), u16(0), u16(1), u16(1), u32(cd.length), u32(cdOffset), u16(0)]);
  return { apk: Buffer.concat([local, block, cd, eocd]), local, cd };
}


module.exports = { fakeApk, u16, u32, u64 };
