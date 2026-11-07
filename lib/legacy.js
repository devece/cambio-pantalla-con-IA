'use strict';
/*
 * Columnas de la tabla `orders` que vienen de cuando Tablero cobraba (plan, extras, monto y moneda).
 * La base de datos real todavía las exige (`not null`), así que cada pedido nuevo las trae con estos valores fijos.
 * Ya no significan nada: Tablero es gratis. No se borran de la base para no tocar datos existentes.
 */
const LEGACY_ORDER_COLUMNS = Object.freeze({ plan: 'completo', addons: {}, total: 0, currency: 'USD' });

/** El nivel que se le informa a la app al activar un código. Es uno solo (lo incluye todo); ver Tier en la app. */
const LICENSE_PLAN = 'completo';

module.exports = { LEGACY_ORDER_COLUMNS, LICENSE_PLAN };
