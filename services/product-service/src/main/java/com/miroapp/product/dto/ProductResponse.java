package com.miroapp.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.miroapp.common.serializer.MoneySerializer;
import com.miroapp.product.entity.ProductType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// ProductResponse — DTO de respuesta para productos
// =====================================================================
// Incluye categoryName para evitar segunda llamada del frontend.
// @JsonInclude(NON_NULL) → campos null no aparecen en el JSON.
// =====================================================================
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

    private UUID        id;
    private UUID        categoryId;
    private String      categoryName;  // evita segunda llamada
    private String      name;
    private String      description;
    private ProductType type;
    private String      sku;
    private String      barcode;
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal  basePrice;
    @JsonSerialize(using = MoneySerializer.class)
    private BigDecimal  baseCost;
    private UUID        taxTypeId;
    private String      unit;
    private Boolean     hasVariants;
    private Boolean     trackStock;
    private String      imageUrl;
    private Boolean     active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}