'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const db = require('../lib/supabase.js');
const { FORMAT } = require('../lib/license.js');

/* Base de datos en memoria que imita lo mínimo de PostgREST que usa el panel. */
function fakeDb() {
  const orders = [];
  const licenses = [];
  const dup = () => Object.assign(new Error('Supabase 409'), { status: 409 });
  const eq = (path, col) => (new RegExp(`${col}=eq\\.([^&]+)`).exec(path) || [])[1];
  db.configured = () => true;
  db.rest = async (path, { method = 'GET', body } = {}) => {
    const table = path.split('?')[0];
    const rows = table === 'orders' ? orders : licenses;
    if (method === 'POST') {
      if (table === 'licenses' && licenses.some((l) => l.code === body.code)) throw dup();
      const row = Object.assign({ created_at: new Date().toISOString(), transfers_used: 0, device_hash: null, activated_at: null }, body);
      rows.push(row);
      return [row];
    }
    if (method === 'PATCH') {
      const key = table === 'orders' ? 'public_id' : 'code';
      const hit = rows.filter((r) => r[key] === decodeURIComponent(eq(path, key)));
      hit.forEach((r) => Object.assign(r, body));
      return hit;
    }
    const byOrder = eq(path, 'order_public_id');
    const byId = eq(path, 'public_id');
    const byCode = eq(path, 'code');
    return rows.filter((r) => (byOrder ? r.order_public_id === byOrder : byId ? r.public_id === byId : byCode ? r.code === decodeURIComponent(byCode) : true));
  };
  return { orders, licenses };
}

const res = () => {
  const r = { code: 200, body: null, headers: {} };
  r.setHeader = (k, v) => { r.headers[k] = v; };
  r.status = (c) => { r.code = c; return r; };
  r.json = (b) => { r.body = b; return r; };
  return r;
};
let n = 0;
const call = async (body, token = 'clave-secreta') => {
  const handler = require('../api/admin.js');
  const r = res();
  await handler({ method: 'POST', headers: { 'x-admin-token': token, 'x-forwarded-for': 'adm-' + n++ }, body }, r);
  return r;
};

test('sin ADMIN_TOKEN o con clave incorrecta no entra nadie', async () => {
  fakeDb();
  delete process.env.ADMIN_TOKEN;
  assert.equal((await call({ action: 'list' })).code, 503);
  process.env.ADMIN_TOKEN = 'clave-secreta';
  assert.equal((await call({ action: 'list' }, 'otra')).code, 401);
  assert.equal((await call({ action: 'list' }, '')).code, 401);
  assert.equal((await call({ action: 'list' })).code, 200);
  assert.equal((await call({ action: 'borrar-todo' })).code, 400);
  assert.equal((await call({ action: 'constructor' })).code, 400);
});

test('crear un código deja el pedido pagado y una licencia con formato válido', async () => {
  const d = fakeDb();
  process.env.ADMIN_TOKEN = 'clave-secreta';
  const r = await call({ action: 'create', plan: 'completo', note: 'Juan, WhatsApp +56 9 1111 2222' });
  assert.equal(r.code, 200, JSON.stringify(r.body));
  assert.match(r.body.code, FORMAT);
  assert.match(r.body.orderId, /^TB-[0-9A-F]{8}$/);
  assert.equal(d.orders[0].status, 'paid');
  assert.equal(d.orders[0].plan, 'completo');
  assert.equal(d.licenses.length, 1);
  assert.equal((await call({ action: 'create', plan: 'gratis' })).code, 400);
});

test('confirmar un pedido pendiente crea su código una sola vez', async () => {
  const d = fakeDb();
  process.env.ADMIN_TOKEN = 'clave-secreta';
  d.orders.push({ public_id: 'TB-0A1B2C3D', status: 'pending', plan: 'esencial', total: 9990, contact_value: '+56912345678' });
  const a = await call({ action: 'confirm', orderId: 'tb-0a1b2c3d' });
  const b = await call({ action: 'confirm', orderId: 'TB-0A1B2C3D' });
  assert.equal(a.code, 200);
  assert.equal(a.body.code, b.body.code, 'confirmar dos veces no duplica el código');
  assert.equal(d.licenses.length, 1);
  assert.equal(d.orders[0].status, 'paid');
  assert.equal((await call({ action: 'confirm', orderId: 'TB-FFFFFFFF' })).code, 404);
  assert.equal((await call({ action: 'confirm', orderId: 'hola' })).code, 400);
});

test('revocar y liberar un código', async () => {
  const d = fakeDb();
  process.env.ADMIN_TOKEN = 'clave-secreta';
  const { body } = await call({ action: 'create', plan: 'esencial' });
  d.licenses[0].device_hash = 'abc';
  const list = await call({ action: 'list' });
  assert.equal(list.body.licenses[0].activated, true);
  assert.ok(!JSON.stringify(list.body).includes('abc'), 'nunca se devuelve el hash de la radio');
  const rel = await call({ action: 'release', code: body.code.toLowerCase() });
  assert.equal(rel.code, 200);
  assert.equal(d.licenses[0].device_hash, null);
  assert.equal(d.licenses[0].transfers_used, 1);
  const rev = await call({ action: 'revoke', code: body.code });
  assert.equal(rev.code, 200);
  assert.equal(d.licenses[0].status, 'revoked');
  assert.equal((await call({ action: 'revoke', code: 'TBL-AAAA' })).code, 400);
});

test('las tablas de Tablero llevan el prefijo tablero_ en la base de datos', () => {
  const { tablePath } = require('../lib/supabase.js');
  assert.equal(tablePath('orders?public_id=eq.TB-1'), 'tablero_orders?public_id=eq.TB-1');
  assert.equal(tablePath('licenses'), 'tablero_licenses');
  assert.equal(tablePath('orders'), 'tablero_orders');
  assert.equal(tablePath('otra_tabla'), 'otra_tabla');
  assert.equal(tablePath('ordersX'), 'ordersX');
});
