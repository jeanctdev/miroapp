package com.miroapp.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "{category.name.required}")
    @Size(min = 2, max = 100, message = "{category.name.size}")
    private String name;

    @Size(max = 500, message = "{category.description.size}")
    private String description;

    private UUID parentId;
    private Short sortOrder;
}