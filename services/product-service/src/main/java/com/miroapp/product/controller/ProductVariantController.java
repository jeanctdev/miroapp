package com.miroapp.product.controller;

import com.miroapp.common.response.ApiResponse;
import com.miroapp.product.config.SecurityUtils;
import com.miroapp.product.dto.CreateVariantRequest;
import com.miroapp.product.dto.PageResponse;
import com.miroapp.product.dto.ProductVariantResponse;
import com.miroapp.product.dto.UpdateVariantRequest;
import com.miroapp.product.service.ProductVariantService;
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
// ProductVariantController — endpoints REST para variantes
// =====================================================================
// Responsabilidades:
//   Recibir el request HTTP
//   Validar con @Valid → si falla → GlobalExceptionHandler → 400
//   Llamar al service — SIN lógica de negocio aquí
//   Devolver ApiResponse.ok() con el status correcto
//
// @PageableDefault para variantes:
//   size=20     → 20 variantes por página
//   sort=sortOrder → ordenado por sort_order ASC
//   El dueño define el orden visual de las variantes en el POS
//
// @PreAuthorize:
//   CASHIER y VIEWER → solo lectura (necesitan ver variantes en POS)
//   MANAGER y TENANT_ADMIN → pueden crear y editar
//   TENANT_ADMIN → único que puede eliminar
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductVariantController {

  private final ProductVariantService variantService;
  private final SecurityUtils securityUtils;

  // ── GET /api/products/{productId}/variants ────────────────────
  // Lista todas las variantes activas de un producto.
  // El POS llama esto cuando el cajero selecciona un producto
  // con hasVariants=true para mostrar las opciones disponibles.
  // ?page=0&size=20&sort=sortOrder,asc
  @GetMapping("/{productId}/variants")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<ProductVariantResponse>>> findByProduct(
    @PathVariable UUID productId,
    @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable,
    HttpServletRequest request) {

    Page<ProductVariantResponse> page = variantService.findByProduct(productId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

  // ── GET /api/products/{productId}/variants/{id} ───────────────
  // Obtiene una variante específica por su UUID.
  // Verifica que la variante pertenece al producto indicado.
  @GetMapping("/{productId}/variants/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductVariantResponse>> findById(
    @PathVariable UUID productId,
    @PathVariable UUID id,
    HttpServletRequest request) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        variantService.findById(productId, id),
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

  // ── GET /api/products/variants/sku/{sku} ──────────────────────
  // Búsqueda directa por SKU.
  // El cajero escribe el SKU manualmente en el POS.
  // No necesita saber el productId — el SKU es único por tenant.
  // Es la búsqueda directa más rápida cuando no hay scanner.
  @GetMapping("/variants/sku/{sku}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductVariantResponse>> findBySku(
    @PathVariable String sku,
    HttpServletRequest request) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        variantService.findBySku(sku),
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

  // ── GET /api/products/variants/barcode/{code} ─────────────────
  // Búsqueda por código de barras — scanner del POS.
  // El cajero pasa el producto por el lector físico.
  // El scanner envía el barcode → sistema devuelve la variante.
  // Con resolvedPrice incluido → el POS cobra directamente.
  @GetMapping("/variants/barcode/{code}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<ProductVariantResponse>> findByBarcode(
    @PathVariable String code,
    HttpServletRequest request) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        variantService.findByBarcode(code),
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

  // ── POST /api/products/{productId}/variants ───────────────────
  // Crea una nueva variante para un producto.
  // El productId en la URL define el padre.
  // El body define los atributos de la variante.
  //
  // Validaciones en el service:
  //   → Producto existe
  //   → Producto es PHYSICAL
  //   → Producto tiene hasVariants=true
  //   → SKU y barcode únicos
  @PostMapping("/{productId}/variants")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<ProductVariantResponse>> create(
    @PathVariable UUID productId,
    @Valid @RequestBody CreateVariantRequest request,
    HttpServletRequest httpRequest) {

    ProductVariantResponse response =
      variantService.create(productId, request);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.ok(
        response,
        httpRequest.getRequestURI(),
        HttpStatus.CREATED.value()
      ));
  }

  // ── PUT /api/products/{productId}/variants/{id} ───────────────
  // Actualiza una variante existente.
  // Solo los campos enviados se modifican — patch-like behavior.
  // Verifica que la variante pertenece al producto indicado.
  @PutMapping("/{productId}/variants/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<ProductVariantResponse>> update(
    @PathVariable UUID productId,
    @PathVariable UUID id,
    @Valid @RequestBody UpdateVariantRequest request,
    HttpServletRequest httpRequest) {

    ProductVariantResponse response =
      variantService.update(productId, id, request);

    return ResponseEntity.ok(
      ApiResponse.ok(
        response,
        httpRequest.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

  // ── DELETE /api/products/{productId}/variants/{id} ────────────
  // Soft delete de una variante — solo TENANT_ADMIN.
  // La variante queda en BD con deleted_at = now().
  // Si esa variante aparece en ventas históricas → sigue ahí.
  // Los datos son inmutables — la auditoría es sagrada.
  @DeleteMapping("/{productId}/variants/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(
    @PathVariable UUID productId,
    @PathVariable UUID id,
    HttpServletRequest request) {

    variantService.delete(productId, id);

    return ResponseEntity.ok(
      ApiResponse.ok(
        null,
        request.getRequestURI(),
        HttpStatus.OK.value()
      )
    );
  }

}
