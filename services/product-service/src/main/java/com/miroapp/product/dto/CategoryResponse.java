package com.miroapp.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// CategoryResponse — DTO de respuesta para categorías
// =====================================================================
// NUNCA devolvemos la entidad directamente al cliente.
// El DTO controla exactamente qué campos expone la API.
// @JsonInclude(NON_NULL) → campos null no aparecen en el JSON.
// =====================================================================
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {
    private UUID id;
    private String name;
    private String description;

    // NULL → categoría raíz | UUID → subcategoría
    private UUID   parentId;

    // Nombre del padre — evita segunda llamada al frontend
    private String parentName;

    private Short           sortOrder;
    private Boolean         active;
    private OffsetDateTime createdAt;
    private OffsetDateTime  updatedAt;
}
