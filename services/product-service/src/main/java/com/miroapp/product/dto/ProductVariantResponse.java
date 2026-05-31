package com.miroapp.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.miroapp.common.serializer.MoneySerializer;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// ProductVariantResponse — DTO de respuesta para variantes
// =====================================================================
// ¿QUÉ ES UN DTO DE RESPUESTA?
//   Data Transfer Object — el objeto que devolvemos al cliente.
//   NUNCA devolvemos la entidad directamente porque:
//     La entidad tiene campos internos (createdBy, deletedAt)
//     que no debe ver el cliente.
//     El DTO controla exactamente qué se expone.
//
// DATA ENRICHMENT:
//   productName  → nombre del padre, evita segunda llamada
//   resolvedPrice → precio real que usa el POS
//   resolvedCost  → costo real para el margen
//
//   ¿Por qué resolvedPrice?
//   El cajero en el POS necesita saber el precio final.
//   Si price es null → usa el del padre.
//   El frontend no debería hacer esa lógica — el backend la resuelve.
//
// @JsonInclude(NON_NULL):
//   Campos null no aparecen en el JSON.
//   Si una variante no tiene barcode → "barcode" no aparece.
//   JSON más limpio y liviano.
//
// @JsonSerialize(using = MoneySerializer.class):
//   Convierte BigDecimal de 4 decimales → 2 decimales.
//   25.0000 → 25.00 — cumple SUNAT, limpio para el frontend.
// =====================================================================
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductVariantResponse {

  private UUID   id;
  private UUID productId;

  // Nombre del producto padre — data enrichment
  // Evita que el frontend haga GET /api/products/{id}
  // solo para mostrar "Collar Nylon Ajustable - Talla M"
  private String productName;

  // Nombre de la variante — el atributo diferenciador
  // Ejemplo: "Talla S", "3kg", "Sabor Pollo y Arroz"
  private String name;

  private String sku;
  private String barcode;

  // Precio propio de la variante — puede ser null
  // Si es null → la variante hereda el precio del padre
  // El frontend usa resolvedPrice para mostrar siempre
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal price;

  // Costo propio de la variante — puede ser null
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal cost;

  // Precio resuelto — lo que el POS usa realmente
  // price != null → resolvedPrice = price
  // price == null → resolvedPrice = products.base_price
  // SIEMPRE tiene valor — el POS nunca ve null aquí
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal resolvedPrice;

  // Costo resuelto — para calcular margen en reportes
  // cost != null → resolvedCost = cost
  // cost == null → resolvedCost = products.base_cost
  @JsonSerialize(using = MoneySerializer.class)
  private BigDecimal resolvedCost;

  // Atributos JSONB como String
  // El frontend parsea y muestra como chips/tags
  // {"talla": "M", "color": "rojo"}
  private String  attributes;

  // URL de imagen — null si hereda la imagen del padre
  private String  imageUrl;

  private Short   sortOrder;
  private Boolean active;

  // Timestamps en UTC — termina en Z
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

}
