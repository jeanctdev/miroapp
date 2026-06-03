package com.miroapp.inventory.dto;

import com.miroapp.inventory.entity.MovementReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

// =====================================================================
// StockAdjustmentRequest — DTO para ajuste manual de stock
// =====================================================================
// ¿Qué es un ajuste manual?
//   El dueño hace un conteo físico y encuentra diferencias:
//   "El sistema dice 10 unidades pero en el estante hay 8"
//   → Ajuste negativo de 2 (ADJUSTMENT_OUT)
//
//   O recibe mercancía sin orden de compra:
//   "Llegaron 5 unidades extra de regalo del proveedor"
//   → Ajuste positivo de 5 (ADJUSTMENT_IN)
//
// ¿Qué razones aplican aquí?
//   Solo las razones de ajuste y transferencia:
//   ADJUSTMENT_IN  → aumentar stock manualmente
//   ADJUSTMENT_OUT → reducir stock manualmente
//   PURCHASE       → entrada por compra a proveedor
//   RETURN_CUSTOMER → cliente devolvió producto
//   RETURN_SUPPLIER → devolvemos producto al proveedor
//
//   SALE → NO aplica aquí — la venta descuenta stock
//          automáticamente en sales-service (futuro)
//
// ¿Por qué notes es obligatorio?
//   Los ajustes manuales afectan el libro contable.
//   SUNAT puede auditar diferencias de inventario.
//   Sin justificación → no se acepta el ajuste.
// =====================================================================
@Getter
@NoArgsConstructor
public class StockAdjustmentRequest {

  // Sucursal donde se registra el ajuste
  @NotNull(message = "{adjustment.branch.required}")
  private UUID branchId;

  // Producto a ajustar — exclusivo con variantId
  // Enviar productId O variantId, no los dos
  private UUID productId;

  // Variante a ajustar — exclusivo con productId
  private UUID variantId;

  // Razón del ajuste — define si suma o resta
  @NotNull(message = "{adjustment.reason.required}")
  private MovementReason reason;

  // Cantidad a ajustar — siempre positivo
  // El service calcula si suma o resta según la reason
  @NotNull(message = "{stock.quantity.zero}")
  @DecimalMin(value = "0.01",
    message = "{stock.quantity.zero}")
  private BigDecimal quantity;

  // Justificación obligatoria del ajuste
  // "Conteo físico del 02/06/2026 — diferencia detectada"
  @NotNull(message = "{movement.notes.required}")
  @Size(min = 5, max = 500,
    message = "{movement.notes.size}")
  private String notes;
}