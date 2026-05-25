package com.miroapp.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class UpdateProductRequest {

    private UUID categoryId;

    @Size(min = 2, max = 200, message = "{product.name.size}")
    private String name;

    private String description;

    @Size(max = 100, message = "{product.sku.size}")
    private String sku;

    @Size(max = 100, message = "{product.barcode.size}")
    private String barcode;

    @DecimalMin(value = "0.0", message = "{product.price.negative}")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.0", message = "{product.cost.negative}")
    private BigDecimal baseCost;

    private UUID taxTypeId;

    @Size(max = 50, message = "{product.unit.size}")
    private String unit;

    private Boolean hasVariants;
    private Boolean trackStock;
    private String imageUrl;
    private Boolean active;
}