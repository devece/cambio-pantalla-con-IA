-- Esquema de Tablero. Las tablas llevan el prefijo `tablero_` para convivir con otras apps en el mismo proyecto de Supabase.
-- Seguridad: RLS activado, SIN políticas y SIN permisos para los roles públicos (anon / authenticated)
-- => las tablas no se pueden leer ni escribir desde ninguna app web; solo el servidor de Tablero, con la service role key.
-- Es solo agregar tablas nuevas: no toca nada de lo que ya exista en el proyecto.

create table if not exists public.tablero_orders (
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
  payment_ref    text,                                 -- id del cobro en el proveedor (PayPal, si se usa)
  charge_amount  numeric(10,2),                        -- monto exacto pedido al proveedor (PayPal va en USD)
  charge_currency text,
  created_at     timestamptz not null default now(),
  paid_at        timestamptz
);

create table if not exists public.tablero_licenses (
  code             text primary key,                   -- TBL-XXXX-XXXX-XXXX
  order_public_id  text not null references public.tablero_orders(public_id),
  status           text not null default 'active' check (status in ('active','revoked')),
  device_hash      text,                               -- HMAC del id de la radio; null = sin activar
  activated_at     timestamptz,
  transfers_used   integer not null default 0,         -- política: 1 traspaso gratis
  created_at       timestamptz not null default now()
);

create index if not exists tablero_orders_status_idx on public.tablero_orders (status, created_at desc);
create index if not exists tablero_licenses_order_idx on public.tablero_licenses (order_public_id);
create unique index if not exists tablero_licenses_one_per_order_idx on public.tablero_licenses (order_public_id); -- una licencia por pedido

alter table public.tablero_orders   enable row level security;
alter table public.tablero_licenses enable row level security;
-- (sin políticas a propósito)

-- Defensa extra: ni siquiera con una política mal puesta en el futuro podrían tocarlas los roles públicos.
revoke all on table public.tablero_orders   from anon, authenticated;
revoke all on table public.tablero_licenses from anon, authenticated;
