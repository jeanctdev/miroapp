package com.miroapp.inventory.entity;

// =====================================================================
// MovementReason — razón específica del movimiento de stock
// =====================================================================
// Coincide exactamente con el CHECK constraint de la BD:
//   CHECK (reason IN (
//     'PURCHASE','SALE',
//     'TRANSFER_IN','TRANSFER_OUT',
//     'ADJUSTMENT_IN','ADJUSTMENT_OUT',
//     'RETURN_CUSTOMER','RETURN_SUPPLIER'
//   ))
//
// ¿Cuándo se usa cada razón?
//   PURCHASE        → compraste al proveedor        → type IN
//   SALE            → vendiste en el POS             → type OUT
//   TRANSFER_IN     → recibiste de otra sucursal     → type IN
//   TRANSFER_OUT    → enviaste a otra sucursal        → type OUT
//   ADJUSTMENT_IN   → ajuste positivo (conteo físico) → type IN
//   ADJUSTMENT_OUT  → ajuste negativo (merma/vence)  → type OUT
//   RETURN_CUSTOMER → cliente devolvió el producto   → type IN
//   RETURN_SUPPLIER → devolviste al proveedor        → type OUT
//
// ¿Por qué separar type y reason?
//   Flexibilidad en reportes y auditoría:
//   - Ver todas las entradas: WHERE type = 'IN'
//   - Ver solo compras: WHERE reason = 'PURCHASE'
//   - Ver solo ventas: WHERE reason = 'SALE'
// =====================================================================
public enum MovementReason {
  PURCHASE,
  SALE,
  TRANSFER_IN,
  TRANSFER_OUT,
  ADJUSTMENT_IN,
  ADJUSTMENT_OUT,
  RETURN_CUSTOMER,
  RETURN_SUPPLIER
}