-- Esquema base de Tablero. Ejecútalo en un proyecto de Supabase NUEVO y separado de tus otras apps.
-- Seguridad: RLS activado y SIN políticas públicas => solo la service role key (servidor) puede leer o escribir.

create table if not exists public.orders (
  id             uuid primary key default gen_random_uuid(),
  public_id      text not null unique,               -- ej. TB-9F2A41C7 (lo que ve el cliente)
  status         text not null default 'pending'
                 check (status in ('pending','paid','failed','refunded')),
  plan           text not null,
  addons         jsonb not null default '{}'::jsonb,
  total          integer not null check (total >= 0), -- CLP, sin decimales
  currency       text not null default 'CLP',
  contact_kind   text not null check (contact_kind in ('whatsapp','email')),
  contact_value  text not null,
  config         jsonb not null,                       -- radio, auto, estilo, funciones (sin foto)
  payment_ref    text,                                 -- id del cobro en el proveedor
  created_at     timestamptz not null default now(),
  paid_at        timestamptz
);

create table if not exists public.licenses (
  code             text primary key,                   -- TBL-XXXX-XXXX-XXXX
  order_public_id  text not null references public.orders(public_id),
  status           text not null default 'active' check (status in ('active','revoked')),
  device_hash      text,                               -- HMAC del id de la radio; null = sin activar
  activated_at     timestamptz,
  transfers_used   integer not null default 0,         -- política: 1 traspaso gratis
  created_at       timestamptz not null default now()
);

create index if not exists orders_status_idx on public.orders (status, created_at desc);
create index if not exists licenses_order_idx on public.licenses (order_public_id);

alter table public.orders   enable row level security;
alter table public.licenses enable row level security;
-- (sin políticas a propósito)
