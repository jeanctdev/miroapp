package com.miroapp.product.controller;

import com.miroapp.common.response.ApiResponse;
import com.miroapp.product.dto.CategoryResponse;
import com.miroapp.product.dto.CreateCategoryRequest;
import com.miroapp.product.dto.PageResponse;
import com.miroapp.product.dto.UpdateCategoryRequest;
import com.miroapp.product.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// =====================================================================
// CategoryController — endpoints REST para categorías
// =====================================================================
// Responsabilidades:
//   Recibir requests HTTP
//   Validar con @Valid
//   Llamar al service
//   Devolver ApiResponse<T> con el status correcto
//
// NO contiene lógica de negocio — todo está en CategoryService.
//
// @PageableDefault → define valores por defecto de paginación:
//   size=20    → 20 elementos por página
//   sort=sortOrder → ordenado por sort_order ASC
//   Si el cliente no envía ?page y ?size → usa estos defaults
//
// @PreAuthorize → control de acceso por rol:
//   TENANT_ADMIN → puede crear, editar y eliminar categorías
//   MANAGER      → puede crear y editar pero no eliminar
//   CASHIER      → solo lectura
//   VIEWER       → solo lectura
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ── GET /api/categories ───────────────────────────────────────
    // Lista todas las categorías activas paginadas.
    // Usado en el backoffice para gestionar el catálogo.
    // ?page=0&size=20&sort=sortOrder,asc
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> findAll(
            @PageableDefault(size = 20, sort = "sortOrder",
            direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request) {

        Page<CategoryResponse> page = categoryService.findAll(pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        new PageResponse<>(page),
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── GET /api/categories/roots ─────────────────────────────────
    // Lista solo las categorías raíz (sin padre).
    // Punto de entrada del árbol de categorías en el POS.
    @GetMapping("/roots")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> findRoots(
            @PageableDefault(size = 20, sort = "sortOrder",
                    direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request) {

        Page<CategoryResponse> page = categoryService.findRoots(pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        new PageResponse<>(page),
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── GET /api/categories/{id}/children ─────────────────────────
    // Lista las subcategorías directas de una categoría padre.
    // Usado para navegar el árbol en el POS:
    @GetMapping("/{id}/children")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> findChildren(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "sortOrder",
            direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request) {

        Page<CategoryResponse> page =
                categoryService.findByParent(id, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        new PageResponse<>(page),
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── GET /api/categories/search?name=xxx ───────────────────────
    // Búsqueda por nombre — barra de búsqueda del backoffice.
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> search(
            @RequestParam String name,
            @PageableDefault(size = 20, sort = "sortOrder",
            direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletRequest request) {

        Page<CategoryResponse> page =
                categoryService.search(name, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        new PageResponse<>(page),
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── GET /api/categories/{id} ──────────────────────────────────
    // Obtiene una categoría por su UUID.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> findById(
            @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                ApiResponse.ok(
                        categoryService.findById(id),
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── POST /api/categories ──────────────────────────────────────
    // Crea una nueva categoría.
    // Solo TENANT_ADMIN y MANAGER pueden crear categorías.
    // @Valid activa las validaciones del DTO →
    // si falla → GlobalExceptionHandler → 400
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request,
            HttpServletRequest httpRequest) {

        // El userId viene del SecurityContext via SecurityUtils
        // El service lo extrae internamente — el controller
        // no necesita saber quién es el usuario
        CategoryResponse response = categoryService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        response,
                        httpRequest.getRequestURI(),
                        HttpStatus.CREATED.value()
                ));
    }

    // ── PUT /api/categories/{id} ──────────────────────────────────
    // Actualiza una categoría existente.
    // Solo campos enviados se actualizan — patch-like behavior.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request,
            HttpServletRequest httpRequest) {

        CategoryResponse response = categoryService.update(id, request);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        response,
                        httpRequest.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

    // ── DELETE /api/categories/{id} ───────────────────────────────
    // Elimina (soft delete) una categoría.
    // Solo TENANT_ADMIN puede eliminar.
    // Falla si tiene subcategorías o productos activos.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            HttpServletRequest request) {

        categoryService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        request.getRequestURI(),
                        HttpStatus.OK.value()
                )
        );
    }

  // ── GET /api/categories/tree ──────────────────────────────────
  // Devuelve el árbol completo de categorías anidadas.
  // Una sola llamada construye el menú de navegación completo.
  // El frontend no necesita hacer múltiples llamadas por nivel.
  @GetMapping("/tree")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<List<CategoryResponse>>> getTree(
    HttpServletRequest request) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        categoryService.getTree(),
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

}
