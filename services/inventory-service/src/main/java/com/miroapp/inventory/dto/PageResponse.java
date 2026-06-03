package com.miroapp.inventory.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// =====================================================================
// PageResponse — wrapper estándar para listas paginadas en MIRO
// =====================================================================
// Convención MIRO: todas las listas son paginadas.
// Mismo patrón que product-service — consistencia en toda la API.
//
// El frontend siempre recibe:
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
public class PageResponse<T>{

  private final List<T> content;
  private final int     page;
  private final int     size;
  private final long    totalElements;
  private final int     totalPages;
  private final boolean last;

  public PageResponse(Page<T> page) {
    this.content       = page.getContent();
    this.page          = page.getNumber();
    this.size          = page.getSize();
    this.totalElements = page.getTotalElements();
    this.totalPages    = page.getTotalPages();
    this.last          = page.isLast();
  }
}
