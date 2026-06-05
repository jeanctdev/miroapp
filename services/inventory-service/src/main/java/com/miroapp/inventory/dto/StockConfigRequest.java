package com.miroapp.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// =====================================================================
// StockConfigRequest — configura alertas de stock por producto/sucursal
// =====================================================================
// ¿Para qué sirve?
//   El encargado del almacén define dos niveles
//   para cada producto en cada sucursal:
//
//   min_quantity → nivel mínimo de alerta:
//     Cuando quantity <= min_quantity el sistema notifica.
//     "Cuando queden menos de 5 Amoxicilinas → avisar"
//     0 = sin alerta configurada para este producto
//
//   max_quantity → nivel máximo de reposición:
//     Cuánto es el tope recomendado de stock.
//     Útil para evitar sobre-stock en sucursales pequeñas.
//     null = sin límite configurado
//
// ¿Por qué es un endpoint separado del ajuste?
//   min_quantity y max_quantity son CONFIGURACIÓN del negocio.
//   No son movimientos de stock — no generan StockMovement.
//   Mezclarlos con los ajustes confundiría el historial.
//   Separados → responsabilidad única y clara.
//
// ¿Por qué todos los campos son opcionales?
//   Patch-like behavior — el encargado puede actualizar
//   solo el mínimo sin tocar el máximo y viceversa.
//   Solo se actualizan los campos que vienen en el request.
// =====================================================================
@Getter
@NoArgsConstructor
public class StockConfigRequest {
  // Nivel mínimo de alerta — opcional
  // Si se envía → debe ser >= 0
  // null → no se modifica el valor actual
  @DecimalMin(
    value = "0.0",
    message = "{stock.min.quantity.negative}")
  private BigDecimal minQuantity;

  // Nivel máximo de reposición — opcional
  // null → sin límite (se acepta y guarda como null)
  // Si se envía → debe ser >= 0
  @DecimalMin(
    value = "0.0",
    message = "{stock.min.quantity.negative}")
  private BigDecimal maxQuantity;
}
