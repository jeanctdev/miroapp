package com.miroapp.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.miroapp.common.serializer.MoneySerializer;
import com.miroapp.inventory.entity.MovementReason;
import com.miroapp.inventory.entity.MovementType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// StockMovementResponse — DTO de respuesta para movimientos de stock
// =====================================================================
// ¿Para qué se usa?
//   Historial de inventario en el backoffice.
//   El dueño del negocio puede ver:
//   → Qué entró y qué salió (type + reason)
//   → Cuánto había antes y después (stockBefore + stockAfter)
//   → Quién lo registró (createdBy)
//   → A qué documento corresponde (referenceId)
//
// @JsonInclude(NON_NULL):
//   referenceId null → no aparece (ajuste manual sin documento)
//   notes null → no aparece (movimientos automáticos sin notas)
//   variantId null → no aparece (producto sin variantes)
//   productId null → no aparece (producto con variantes)
// =====================================================================
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockMovementResponse {

  private UUID id;
  private UUID branchId;

  // Producto o variante que tuvo el movimiento
  private UUID productId;
  private UUID variantId;

  // Dirección: IN (entrada) | OUT (salida)
  private MovementType type;

  // Motivo específico del movimiento
  private MovementReason reason;

  // Cantidad que entró o salió — siempre positivo
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal quantity;

  // Stock antes del movimiento — para auditoría
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal stockBefore;

  // Stock después del movimiento
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal stockAfter;

  // ID del documento que originó el movimiento
  // null → ajuste manual sin documento
  private UUID referenceId;

  // Notas del movimiento — obligatorio en ajustes manuales
  private String notes;

  // Quién registró el movimiento
  private UUID createdBy;

  private OffsetDateTime createdAt;
}