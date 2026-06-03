package com.miroapp.inventory.entity;

// =====================================================================
// MovementType — dirección del movimiento de stock
// =====================================================================
// Coincide exactamente con el CHECK constraint de la BD:
//   CHECK (type IN ('IN','OUT'))
//
// IN  → stock sube  (compra, transferencia entrante, ajuste+)
// OUT → stock baja  (venta, transferencia saliente, ajuste-)
//
// ¿Por qué solo dos valores y no más?
//   La dirección (IN/OUT) es independiente de la razón.
//   La razón específica va en el campo "reason" (MovementReason).
//   Esta separación permite filtrar por dirección sin importar
//   el motivo: "¿cuánto entró este mes?" → WHERE type = 'IN'
// =====================================================================
public enum MovementType {
  IN,
  OUT
}
