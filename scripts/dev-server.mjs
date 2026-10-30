// Servidor local sin dependencias: sirve /public y ejecuta api/*.js como lo haría Vercel.
// Uso: npm run dev  ->  http://localhost:3000
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pub = path.join(root, 'public');
const require = createRequire(import.meta.url);
const PORT = Number(process.env.PORT) || 3000;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
};

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => {
      data += c;
      if (data.length > 64 * 1024) req.destroy();
    });
    req.on('end', () => resolve(data));
    req.on('error', () => resolve(''));
  });
}

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');

    if (url.pathname.startsWith('/api/')) {
      const name = url.pathname.slice(5).replace(/[^a-z0-9-]/gi, '');
      let handler;
      try {
        handler = require(path.join(root, 'api', name + '.js'));
      } catch {
        res.writeHead(404, { 'content-type': 'application/json' });
        return res.end(JSON.stringify({ ok: false, error: 'No existe' }));
      }
      const raw = await readBody(req);
      try {
        req.body = raw ? JSON.parse(raw) : undefined;
      } catch {
        req.body = undefined;
      }
      req.query = Object.fromEntries(url.searchParams);
      res.status = (code) => {
        res.statusCode = code;
        return res;
      };
      res.json = (obj) => {
        res.setHeader('content-type', 'application/json; charset=utf-8');
        res.end(JSON.stringify(obj));
      };
      try {
        return await handler(req, res);
      } catch (err) {
        console.error(err);
        return res.status(500).json({ ok: false, error: 'Error interno' });
      }
    }

    let rel = decodeURIComponent(url.pathname);
    if (rel.endsWith('/')) rel += 'index.html';
    const file = path.normalize(path.join(pub, rel));
    if (!file.startsWith(pub + path.sep)) {
      res.writeHead(403);
      return res.end('Prohibido');
    }
    try {
      const buf = await readFile(file);
      res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
      res.end(buf);
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('No encontrado');
    }
  })
  .listen(PORT, () => console.log(`Tablero en http://localhost:${PORT}`));
