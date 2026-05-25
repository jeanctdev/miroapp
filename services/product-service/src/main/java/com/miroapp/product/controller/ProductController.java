package com.miroapp.product.controller;

import com.miroapp.common.response.ApiResponse;
import com.miroapp.product.config.SecurityUtils;
import com.miroapp.product.dto.CreateProductRequest;
import com.miroapp.product.dto.PageResponse;
import com.miroapp.product.dto.ProductResponse;
import com.miroapp.product.dto.UpdateProductRequest;
import com.miroapp.product.entity.ProductType;
import com.miroapp.product.service.ProductService;
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

import java.util.UUID;

// =====================================================================
// ProductController — endpoints REST para productos
// =====================================================================
// Responsabilidades:
//   Recibir el request HTTP
//   Validar con @Valid → si falla → GlobalExceptionHandler → 400
//   Llamar al service — SIN lógica de negocio aquí
//   Devolver ApiResponse.ok() con el status correcto
//
// Endpoints de búsqueda:
//   /search    → búsqueda combinada nombre + categoría
//   /sku/{sku} → búsqueda directa por SKU en el POS
//   /barcode/{code} → búsqueda por scanner en el POS
//
// Todos los listados usan Page<T> + Pageable.
// Defaults: page=0, size=20, sort=name ASC
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;
  private final SecurityUtils securityUtils;

  // ── GET /api/products ─────────────────────────────────────────
  // Lista todos los productos activos paginados.
  // Soporta filtros opcionales via query params:
  //   ?type=PHYSICAL    → filtrar por tipo
  //   ?categoryId=uuid  → filtrar por categoría
  // Si no se envía ningún filtro → devuelve todos
  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> findAll(
          @RequestParam(required = false) ProductType type,
          @RequestParam(required = false) UUID categoryId,
          @PageableDefault(size = 20, sort = "name",
                  direction = Sort.Direction.ASC) Pageable pageable,
          HttpServletRequest request) {

    Page<ProductResponse> page;

    // El controller delega la decisión de qué query usar al service
    // según los filtros recibidos
    if (type != null) {
      page = productService.findByType(type, pageable);
    } else if (categoryId != null) {
      page = productService.findByCategory(categoryId, pageable);
    } else {
      page = productService.findAll(pageable);
    }

    return ResponseEntity.ok(
            ApiResponse.ok(
                    new PageResponse<>(page),
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── GET /api/products/search ──────────────────────────────────
  // Búsqueda combinada por nombre y/o categoría.
  // ?name=collar              → busca por nombre
  // ?name=collar&categoryId=  → busca por nombre dentro de categoría
  // ?categoryId=uuid          → todos los de esa categoría
  // Usado en la barra de búsqueda del POS y del backoffice.
  @GetMapping("/search")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
          @RequestParam(required = false) String name,
          @RequestParam(required = false) UUID categoryId,
          @PageableDefault(size = 20, sort = "name",
                  direction = Sort.Direction.ASC) Pageable pageable,
          HttpServletRequest request) {

    Page<ProductResponse> page =
            productService.search(name, categoryId, pageable);

    return ResponseEntity.ok(
            ApiResponse.ok(
                    new PageResponse<>(page),
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── GET /api/products/sku/{sku} ───────────────────────────────
  // Búsqueda directa por SKU.
  // El cajero escribe el SKU manualmente en el POS.
  // Retorna UN solo producto — el SKU es único por tenant.
  @GetMapping("/sku/{sku}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductResponse>> findBySku(
          @PathVariable String sku,
          HttpServletRequest request) {

    return ResponseEntity.ok(
            ApiResponse.ok(
                    productService.findBySku(sku),
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── GET /api/products/barcode/{code} ──────────────────────────
  // Búsqueda por código de barras — scanner del POS.
  // El cajero escanea el producto físico con el lector.
  // Retorna UN solo producto — el barcode es único por tenant.
  @GetMapping("/barcode/{code}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductResponse>> findByBarcode(
          @PathVariable String code,
          HttpServletRequest request) {

    return ResponseEntity.ok(
            ApiResponse.ok(
                    productService.findByBarcode(code),
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── GET /api/products/{id} ────────────────────────────────────
  // Obtiene un producto específico por UUID.
  // Usado en el backoffice para ver detalle del producto.
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductResponse>> findById(
          @PathVariable UUID id,
          HttpServletRequest request) {

    return ResponseEntity.ok(
            ApiResponse.ok(
                    productService.findById(id),
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── POST /api/products ────────────────────────────────────────
  // Crea un nuevo producto en el catálogo.
  // Solo TENANT_ADMIN y MANAGER pueden crear productos.
  // @Valid activa las validaciones del DTO:
  //   nombre requerido, precio >= 0, type no nulo, etc.
  // Si falla → GlobalExceptionHandler → 400 con el campo
  @PostMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<ProductResponse>> create(
          @Valid @RequestBody CreateProductRequest request,
          HttpServletRequest httpRequest) {

    ProductResponse response = productService.create(request);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(
                    response,
                    httpRequest.getRequestURI(),
                    HttpStatus.CREATED.value()
            ));
  }

  // ── PUT /api/products/{id} ────────────────────────────────────
  // Actualiza un producto existente.
  // Solo campos enviados se modifican — patch-like behavior.
  // El type NO se puede cambiar — no está en UpdateProductRequest.
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<ProductResponse>> update(
          @PathVariable UUID id,
          @Valid @RequestBody UpdateProductRequest request,
          HttpServletRequest httpRequest) {

    ProductResponse response = productService.update(id, request);

    return ResponseEntity.ok(
            ApiResponse.ok(
                    response,
                    httpRequest.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

  // ── DELETE /api/products/{id} ─────────────────────────────────
  // Soft delete — solo TENANT_ADMIN puede eliminar productos.
  // El producto queda en la BD con deleted_at = now().
  // Sigue apareciendo en ventas históricas — los datos son inmutables.
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(
          @PathVariable UUID id,
          HttpServletRequest request) {

    productService.delete(id);

    return ResponseEntity.ok(
            ApiResponse.ok(
                    null,
                    request.getRequestURI(),
                    HttpStatus.OK.value()
            )
    );
  }

}
