package com.miroapp.product.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// =====================================================================
// PageResponse — wrapper estándar para listas paginadas en MIRO
// =====================================================================
// REGLA MIRO: todas las listas son paginadas.
// Este DTO envuelve la respuesta paginada de Spring en un formato
// limpio y consistente para el frontend.
//
// El frontend recibe siempre:
// {
//   "success": true,
//   "data": {
//     "content": [...],
//     "page": 0,
//     "size": 20,
//     "totalElements": 150,
//     "totalPages": 8,
//     "last": false
//   }
// }
// =====================================================================
@Getter
public class PageResponse<T> {

    // Lista de elementos de la página actual
    private final List<T> content;

    // Número de página actual (empieza en 0)
    private final int page;

    // Tamaño de página solicitado
    private final int size;

    // Total de elementos en todas las páginas
    private final long totalElements;

    // Total de páginas disponibles
    private final int totalPages;

    // true → esta es la última página
    private final boolean last;

    // Constructor que recibe un Page<T> de Spring Data
    // y extrae la información de paginación automáticamente
    public PageResponse(Page<T> page) {
        this.content       = page.getContent();
        this.page          = page.getNumber();
        this.size          = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages    = page.getTotalPages();
        this.last          = page.isLast();
    }
}