package com.miroapp.product.dto;

import com.miroapp.product.entity.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateProductRequest {

    private UUID categoryId;

    @NotBlank(message = "{product.name.required}")
    @Size(min = 2, max = 200, message = "{product.name.size}")
    private String name;

    private String description;

    @NotNull(message = "{product.type.required}")
    private ProductType type;

    @Size(max = 100, message = "{product.sku.size}")
    private String sku;

    @Size(max = 100, message = "{product.barcode.size}")
    private String barcode;

    @NotNull(message = "{product.price.required}")
    @DecimalMin(value = "0.0", message = "{product.price.negative}")
    private BigDecimal basePrice;

    @NotNull(message = "{product.cost.required}")
    @DecimalMin(value = "0.0", message = "{product.cost.negative}")
    private BigDecimal baseCost;

    private UUID taxTypeId;

    @Size(max = 50, message = "{product.unit.size}")
    private String unit;

    private Boolean hasVariants;
    private Boolean trackStock;
    private String imageUrl;
}