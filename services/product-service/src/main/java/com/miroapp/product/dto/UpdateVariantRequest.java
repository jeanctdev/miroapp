package com.miroapp.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// =====================================================================
// UpdateVariantRequest — DTO de entrada para actualizar variante
// =====================================================================
// Todos los campos son opcionales — solo se actualizan
// los campos enviados en el request (patch-like behavior).
//
// Si el campo no viene en el JSON → null → no se toca.
// Si el campo viene → se actualiza con el nuevo valor.
//
// Ejemplo:
//   PUT /api/products/{productId}/variants/{id}
//   Body: {"price": 30.00}
//   → Solo actualiza el precio, todo lo demás queda igual.
// =====================================================================
@Getter
@NoArgsConstructor
public class UpdateVariantRequest {
  @Size(min = 2, max = 200, message = "{variant.name.size}")
  private String name;

  @Size(max = 100, message = "{variant.sku.size}")
  private String sku;

  @Size(max = 100, message = "{variant.barcode.size}")
  private String barcode;

  @DecimalMin(value = "0.0", message = "{variant.price.negative}")
  private BigDecimal price;

  @DecimalMin(value = "0.0", message = "{variant.cost.negative}")
  private BigDecimal cost;

  private String  attributes;
  private String  imageUrl;
  private Short   sortOrder;
  private Boolean active;
}
