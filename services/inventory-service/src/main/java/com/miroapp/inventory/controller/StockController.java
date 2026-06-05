package com.miroapp.inventory.controller;

import com.miroapp.common.response.ApiResponse;
import com.miroapp.inventory.dto.*;
import com.miroapp.inventory.service.StockService;
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
import com.miroapp.inventory.dto.StockConfigRequest;
import com.miroapp.inventory.entity.MovementReason;
import com.miroapp.inventory.entity.MovementType;

import java.util.UUID;

// =====================================================================
// StockController — endpoints REST del inventario
// =====================================================================
// Responsabilidades del controller (solo estas):
//   → Recibir el request HTTP
//   → Validar con @Valid → si falla → GlobalExceptionHandler → 400
//   → Llamar al service
//   → Devolver ApiResponse con el status correcto
//   → NUNCA contiene lógica de negocio
//
// @PageableDefault para stock:
//   size=20  → 20 registros por página
//   sort=lastUpdatedAt → más recientemente actualizados primero
//   direction=DESC → más reciente primero
//
// Roles:
//   Ver stock/historial → TENANT_ADMIN, MANAGER, CASHIER, VIEWER
//   Ajustes/transferencias → TENANT_ADMIN, MANAGER
//   CASHIER no puede ajustar — operación administrativa
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

  private final StockService stockService;

  // ── GET /api/stock?branchId=uuid ──────────────────────────────
  // Stock actual de toda una sucursal paginado.
  // Vista principal del inventario en el backoffice.
  // branchId como @RequestParam — es un filtro, no un recurso.
  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockResponse>>>
  findByBranch(
    @RequestParam UUID branchId,
    @PageableDefault(size = 20, sort = "lastUpdatedAt", direction = Sort.Direction.DESC)
    Pageable pageable,
    HttpServletRequest request) {

    Page<StockResponse> page =
      stockService.findByBranch(branchId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── GET /api/stock/low?branchId=uuid ──────────────────────────
  // Productos con stock bajo el mínimo configurado.
  // Alerta de reposición — solo registros con min_quantity > 0.
  // El dueño/encargado revisa esto para hacer pedidos.
  @GetMapping("/low")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockResponse>>>
  findLowStock(
    @RequestParam UUID branchId,
    @PageableDefault(
      size = 20,
      sort = "quantity",
      direction = Sort.Direction.ASC)
    Pageable pageable,
    HttpServletRequest request) {

    // ASC → los más críticos (menor stock) aparecen primero
    Page<StockResponse> page =
      stockService.findLowStock(branchId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── GET /api/stock/product/{productId} ────────────────────────
  // Stock de un producto específico en TODAS las sucursales.
  // Útil para saber en qué sucursales hay disponibilidad.
  // Ejemplo: "¿Dónde hay Royal Canin 15kg?"
  @GetMapping("/product/{productId}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockResponse>>>
  findByProduct(
    @PathVariable UUID productId,
    @PageableDefault(
      size = 20,
      sort = "quantity",
      direction = Sort.Direction.DESC)
    Pageable pageable,
    HttpServletRequest request) {

    Page<StockResponse> page =
      stockService.findByProduct(productId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── GET /api/stock/variant/{variantId} ────────────────────────
  // Stock de una variante específica en TODAS las sucursales.
  // Ejemplo: "¿Dónde hay Collar Talla M?"
  @GetMapping("/variant/{variantId}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockResponse>>>
  findByVariant(
    @PathVariable UUID variantId,
    @PageableDefault(
      size = 20,
      sort = "quantity",
      direction = Sort.Direction.DESC)
    Pageable pageable,
    HttpServletRequest request) {

    Page<StockResponse> page =
      stockService.findByVariant(variantId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── POST /api/stock/adjustments ───────────────────────────────
  // Ajuste manual de stock por un administrador o encargado.
  // Crea un StockMovement INMUTABLE y actualiza el stock actual.
  // CASHIER no puede ajustar — operación administrativa.
  //
  // Casos de uso:
  //   → Conteo físico con diferencias (ADJUSTMENT_IN/OUT)
  //   → Entrada de compra a proveedor (PURCHASE)
  //   → Devolución de cliente (RETURN_CUSTOMER)
  //   → Devolución a proveedor (RETURN_SUPPLIER)
  @PostMapping("/adjustments")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<StockMovementResponse>>
  registerAdjustment(
    @Valid @RequestBody StockAdjustmentRequest request,
    HttpServletRequest httpRequest) {

    StockMovementResponse response =
      stockService.registerAdjustment(request);

    // 201 Created → se creó un movimiento de stock nuevo
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.ok(
        response,
        httpRequest.getRequestURI(),
        HttpStatus.CREATED.value()));
  }

  // ── POST /api/stock/transfers ─────────────────────────────────
  // Transferencia de stock entre sucursales del mismo tenant.
  // @Transactional en el service garantiza:
  //   → TRANSFER_OUT en origen
  //   → TRANSFER_IN en destino
  //   → Ambos o ninguno — no hay stock perdido
  //
  // Retorna el movimiento TRANSFER_IN (destino)
  // que confirma que el stock llegó correctamente.
  @PostMapping("/transfers")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<StockMovementResponse>>
  registerTransfer(
    @Valid @RequestBody StockTransferRequest request,
    HttpServletRequest httpRequest) {

    StockMovementResponse response =
      stockService.registerTransfer(request);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.ok(
        response,
        httpRequest.getRequestURI(),
        HttpStatus.CREATED.value()));
  }

  // ── GET /api/stock/movements ──────────────────────────────────
  // Historial con filtros opcionales.
  // Sin filtros    → todos los movimientos de la sucursal
  // ?type=IN       → solo entradas
  // ?type=OUT      → solo salidas
  // ?reason=SALE   → solo ventas
  // ?reason=PURCHASE → solo compras
  //
  // Solo UN filtro a la vez — type tiene prioridad sobre reason.
  // Si ambos vienen → se usa type.
  @GetMapping("/movements")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>>
  findMovements(
    @RequestParam UUID branchId,
    @RequestParam(required = false) MovementType type,
    @RequestParam(required = false) MovementReason reason,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
    HttpServletRequest request) {

    Page<StockMovementResponse> page;

    // según los filtros recibidos
    if (type != null) {
      page = stockService.findMovementsByType(branchId, type, pageable);
    } else if (reason != null) {
      page = stockService.findMovementsByReason(branchId, reason, pageable);
    } else {
      page = stockService.findMovements(branchId, pageable);
    }

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }


  // ── GET /api/stock/movements/product/{productId} ──────────────
  // Historial de movimientos de un producto específico.
  // Ver toda la actividad de un producto a lo largo del tiempo.
  // Útil para entender por qué el stock de un producto cambió.
  @GetMapping("/movements/product/{productId}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER'," +
    "'VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>>
  findMovementsByProduct(
    @PathVariable UUID productId,
    @PageableDefault(
      size = 20,
      sort = "createdAt",
      direction = Sort.Direction.DESC)
    Pageable pageable,
    HttpServletRequest request) {

    Page<StockMovementResponse> page =
      stockService.findMovementsByProduct(
        productId, pageable);

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(page),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── GET /api/stock/{stockId} ──────────────────────────────────
  // Obtiene un registro de stock por su UUID.
  // El frontend necesita el stockId para llamar
  // al endpoint de configuración PUT /config.
  @GetMapping("/{stockId}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')")
  public ResponseEntity<ApiResponse<StockResponse>>
  findById(@PathVariable UUID stockId, HttpServletRequest request) {

    return ResponseEntity.ok(ApiResponse.ok(
      stockService.findById(stockId),
      request.getRequestURI(),
      HttpStatus.OK.value()));
  }

  // ── PUT /api/stock/{stockId}/config ───────────────────────────
  // Configura min_quantity y max_quantity de un registro.
  // No genera StockMovement — es solo configuración.
  // Solo TENANT_ADMIN y MANAGER pueden configurar alertas.
  @PutMapping("/{stockId}/config")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
  public ResponseEntity<ApiResponse<StockResponse>> updateConfig(
    @PathVariable UUID stockId,
    @Valid @RequestBody StockConfigRequest request,
    HttpServletRequest httpRequest) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        stockService.updateStockConfig(
          stockId, request),
        httpRequest.getRequestURI(),
        HttpStatus.OK.value()));
  }

  // ── GET /api/stock/movements/variant/{variantId} ──────────────
  // Historial de movimientos de una variante específica.
  @GetMapping("/movements/variant/{variantId}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','VIEWER')")
  public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>>
  findMovementsByVariant(
    @PathVariable UUID variantId,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
    Pageable pageable,
    HttpServletRequest request) {

    return ResponseEntity.ok(
      ApiResponse.ok(
        new PageResponse<>(
          stockService.findMovementsByVariant(
            variantId, pageable)),
        request.getRequestURI(),
        HttpStatus.OK.value()));
  }

}
