package com.miroapp.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// =====================================================================
// CreateVariantRequest — DTO de entrada para crear variante
// =====================================================================
// ¿Por qué productId no está en el body?
//   Viene del path variable de la URL:
//   POST /api/products/{productId}/variants
//   La URL ya dice a qué producto pertenece la variante.
//   No tiene sentido repetirlo en el body — sería redundante
//   y podría generar inconsistencias si difieren.
//
// ¿Por qué price y cost son opcionales?
//   price null → hereda products.base_price del padre
//   cost null  → hereda products.base_cost del padre
//   Útil cuando todas las variantes tienen el mismo precio.
//
// ¿Por qué attributes es String y no un objeto?
//   El JSONB es libre — cada negocio define sus atributos.
//   No podemos definir una clase fija para algo que es variable.
//   El frontend envía el JSON como String:
//     '{"talla": "M", "color": "rojo"}'
//   El service valida que sea JSON válido antes de guardar.
// =====================================================================
@Getter
@NoArgsConstructor
public class CreateVariantRequest {

  // Nombre obligatorio — el atributo diferenciador
  // "Talla S", "3kg", "Sabor Pollo y Arroz"
  @NotBlank(message = "{variant.name.required}")
  @Size(min = 2, max = 200, message = "{variant.name.size}")
  private String name;

  // SKU opcional — puede no tener si el negocio no lo usa
  // Si se envía → debe ser único por tenant
  @Size(max = 100, message = "{variant.sku.size}")
  private String sku;

  // Barcode opcional — solo PHYSICAL con código impreso
  @Size(max = 100, message = "{variant.barcode.size}")
  private String barcode;

  // Precio opcional — null = hereda del padre
  // Si se envía → debe ser >= 0
  @DecimalMin(value = "0.0", message = "{variant.price.negative}")
  private BigDecimal price;

  // Costo opcional — null = hereda del padre
  @DecimalMin(value = "0.0", message = "{variant.cost.negative}")
  private BigDecimal cost;

  // Atributos en formato JSON como String
  // NULL → se guarda como "{}" (objeto vacío)
  // Ejemplos:
  //   '{"talla": "M"}'
  //   '{"peso": "15kg", "sabor": "pollo"}'
  private String attributes;

  // URL de imagen específica — null = hereda del padre
  private String  imageUrl;

  // Orden en el POS — null = 0 por defecto
  private Short   sortOrder;
}
