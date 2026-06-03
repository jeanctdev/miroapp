package com.miroapp.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.miroapp.common.serializer.MoneySerializer;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// StockResponse — DTO de respuesta para stock actual
// =====================================================================
// ¿Qué datos necesita el frontend?
//
//   El POS necesita saber:
//   → ¿Hay suficiente stock para vender? (quantity)
//   → ¿Cuándo fue la última actualización? (lastUpdatedAt)
//
//   El backoffice de inventario además necesita:
//   → ¿Está bajo el mínimo? (quantity vs minQuantity)
//   → ¿En qué sucursal? (branchId)
//   → ¿Producto o variante? (productId / variantId)
//
// @JsonInclude(NON_NULL):
//   Si variantId es null → no aparece en el JSON
//   Si productId es null → no aparece en el JSON
//   JSON más limpio sin campos null innecesarios
//
// @JsonSerialize(using = MoneySerializer.class):
//   DECIMAL(19,4) en BD → 2 decimales en JSON
//   8.0000 → 8.00
//   Convención MIRO en todos los campos monetarios y de cantidad
// =====================================================================
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockResponse {

  private UUID id;

  // Sucursal donde está el stock
  private UUID branchId;

  // Producto simple (sin variantes) — null si es variante
  private UUID productId;

  // Variante específica — null si es producto simple
  private UUID variantId;

  // Cantidad disponible actual
  // MoneySerializer → 8.0000 se muestra como 8.00 en JSON
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal quantity;

  // Mínimo configurado para alertas de reposición
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal minQuantity;

  // Máximo configurado — null si sin límite
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal maxQuantity;

  // ¿Está bajo el mínimo?
  // Campo calculado en el service — no viene de la BD
  // El frontend lo usa para mostrar alertas visuales
  // true  → mostrar badge rojo en el POS
  // false → stock normal
  private Boolean belowMinimum;

  // Última vez que cambió la cantidad
  private OffsetDateTime lastUpdatedAt;
  private OffsetDateTime createdAt;
}