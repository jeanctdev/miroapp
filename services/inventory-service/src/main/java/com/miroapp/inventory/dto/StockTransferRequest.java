package com.miroapp.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

// =====================================================================
// StockTransferRequest — DTO para transferencia entre sucursales
// =====================================================================
// ¿Qué es una transferencia?
//   Mover stock de una sucursal a otra dentro del mismo tenant.
//   Ejemplo: La sede principal tiene 20 unidades de Royal Canin
//   y la sucursal Norte necesita 5 → se transfieren.
//
// ¿Qué crea el service internamente?
//   DOS movimientos de stock:
//   1. TRANSFER_OUT en la sucursal origen (stock baja)
//   2. TRANSFER_IN en la sucursal destino (stock sube)
//   Ambos con reference_id igual → trazabilidad completa
//
// ¿Por qué solo un endpoint y no dos ajustes manuales?
//   Atomicidad: si falla el TRANSFER_IN después del OUT
//   el stock desaparece del sistema — catástrofe.
//   Con @Transactional: ambos movimientos o ninguno.
//   La transferencia garantiza integridad del inventario.
// =====================================================================
@Getter
@NoArgsConstructor
public class StockTransferRequest {

  // Sucursal que ENVÍA el stock
  @NotNull(message = "{transfer.branch.required}")
  private UUID fromBranchId;

  // Sucursal que RECIBE el stock
  // El service valida que sea diferente a fromBranchId
  @NotNull(message = "{transfer.branch.required}")
  private UUID toBranchId;

  // Producto a transferir — exclusivo con variantId
  private UUID productId;

  // Variante a transferir — exclusivo con productId
  private UUID variantId;

  // Cantidad a transferir — siempre positivo
  // El service verifica que haya suficiente stock en origen
  @NotNull(message = "{transfer.quantity.required}")
  @DecimalMin(value = "0.01",
    message = "{stock.quantity.zero}")
  private BigDecimal quantity;

  // Notas opcionales de la transferencia
  @Size(max = 500, message = "{movement.notes.size}")
  private String notes;
}