package com.miroapp.inventory.service;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.inventory.config.SecurityUtils;
import com.miroapp.inventory.config.TenantContext;
import com.miroapp.inventory.dto.PageResponse;
import com.miroapp.inventory.dto.StockAdjustmentRequest;
import com.miroapp.inventory.dto.StockMovementResponse;
import com.miroapp.inventory.dto.StockResponse;
import com.miroapp.inventory.dto.StockTransferRequest;
import com.miroapp.inventory.entity.MovementReason;
import com.miroapp.inventory.entity.MovementType;
import com.miroapp.inventory.entity.Stock;
import com.miroapp.inventory.entity.StockMovement;
import com.miroapp.inventory.repository.StockMovementRepository;
import com.miroapp.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// =====================================================================
// StockService — lógica de negocio del inventario
// =====================================================================
// Responsabilidades:
//   → Consultar stock actual por sucursal/producto/variante
//   → Registrar ajustes manuales (ADJUSTMENT_IN/OUT, PURCHASE, etc)
//   → Registrar transferencias entre sucursales (@Transactional)
//   → Consultar historial de movimientos paginado
//
// Convenciones MIRO aplicadas aquí:
//   → tenantContext.set() primera línea de cada método
//   → @Transactional en todos los métodos que escriben en BD
//   → Soft delete — nunca DELETE físico
//   → getMessage() para todos los mensajes
//   → stockBefore/stockAfter calculados aquí, no en controller
//   → updatedAt y lastUpdatedAt actualizados manualmente
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

  private final StockRepository         stockRepository;
  private final StockMovementRepository movementRepository;
  private final TenantContext           tenantContext;
  private final SecurityUtils           securityUtils;
  private final MessageSource           messageSource;

  // ── Helper para mensajes ──────────────────────────────────────
  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code, args, code,
      LocaleContextHolder.getLocale());
  }

  // ================================================================
  // ── GET stock de una sucursal ────────────────────────
  // Vista principal del inventario en el backoffice
  @Transactional(readOnly = true)
  public Page<StockResponse> findByBranch(
    UUID branchId, Pageable pageable) {
    tenantContext.set(securityUtils.getCurrentTenantSlug());
    return stockRepository
      .findAllByBranchId(branchId, pageable)
      .map(this::toResponse);
  }

  // ── GET stock de un producto en todas las sucursales ──────────
  // Ver distribución de un producto entre sucursales
  @Transactional(readOnly = true)
  public Page<StockResponse> findByProduct(
    UUID productId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    return stockRepository.findAllByProductId(productId, pageable)
      .map(this::toResponse);
  }

  // ── GET stock de una variante en todas las sucursales ─────────
  @Transactional(readOnly = true)
  public Page<StockResponse> findByVariant(
    UUID variantId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    return stockRepository.findAllByVariantId(variantId, pageable)
      .map(this::toResponse);
  }

  // ── GET productos bajo el mínimo de una sucursal ──────────────
  // Alerta de reposición — stock crítico
  // Solo productos con min_quantity > 0 (tienen alerta activa)
  @Transactional(readOnly = true)
  public Page<StockResponse> findLowStock(
    UUID branchId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    // Traer registros donde quantity <= min_quantity
    // Se pasa quantity como parámetro de comparación
    // Spring Data lo interpreta como:
    //   WHERE quantity <= :quantity AND min_quantity > :minQuantity
    // Reutilizamos el mismo campo como parámetro de comparación
    // usando un BigDecimal muy grande para el límite superior
    return stockRepository
      .findAllByBranchIdAndQuantityLessThanEqualAndMinQuantityGreaterThan(
        branchId,
        new BigDecimal("999999999999999.9999"), // techo //TODO: validar
        BigDecimal.ZERO,
        pageable)
      .map(this::toResponse);
  }

  // ── GET historial de movimientos de una sucursal ───────────────
  @Transactional(readOnly = true)
  public Page<StockMovementResponse> findMovements(
    UUID branchId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    return movementRepository
      .findAllByBranchIdOrderByCreatedAtDesc(branchId, pageable)
      .map(this::toMovementResponse);
  }

  // ── GET historial de movimientos de un producto ────────────────
  @Transactional(readOnly = true)
  public Page<StockMovementResponse> findMovementsByProduct(
    UUID productId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    return movementRepository
      .findAllByProductIdOrderByCreatedAtDesc(productId, pageable)
      .map(this::toMovementResponse);
  }

  // ================================================================
  // AJUSTE MANUAL DE STOCK
  // ================================================================
  // ── POST /api/stock/adjustments ───────────────────────────────
  // Ajuste manual: PURCHASE, ADJUSTMENT_IN/OUT,
  //                RETURN_CUSTOMER, RETURN_SUPPLIER
  //
  // FLUJO:
  //   1. Resolver type según reason (IN o OUT)
  //   2. Buscar o crear el registro de stock
  //   3. Validar stock suficiente si es OUT
  //   4. Calcular stock_before y stock_after
  //   5. Actualizar stock.quantity
  //   6. Crear StockMovement (inmutable)
  @Transactional
  public StockMovementResponse registerAdjustment(
    StockAdjustmentRequest request) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    // VALIDACIÓN 1 — product_id XOR variant_id
    // Debe enviarse uno o el otro, nunca los dos
    validateProductOrVariant(request.getProductId(), request.getVariantId());

    // PASO 1 — Resolver tipo de movimiento según reason
    MovementType type = resolveType(request.getReason());

    // PASO 2 — Buscar o crear el registro de stock
    Stock stock = findOrCreateStock(
      request.getBranchId(),
      request.getProductId(),
      request.getVariantId(),
      userId);

    // PASO 3 — Validar stock suficiente para salidas
    if (type == MovementType.OUT) {
      validateSufficientStock(stock, request.getQuantity());
    }

    // PASO 4 — Calcular antes y después
    BigDecimal stockBefore = stock.getQuantity();
    BigDecimal stockAfter  = type == MovementType.IN
      ? stockBefore.add(request.getQuantity())
      : stockBefore.subtract(request.getQuantity());

    // PASO 5 — Actualizar stock
    stock.setQuantity(stockAfter);
    stock.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    stockRepository.save(stock);

    // PASO 6 — Crear movimiento INMUTABLE
    StockMovement movement = StockMovement.builder()
      .branchId(request.getBranchId())
      .productId(request.getProductId())
      .variantId(request.getVariantId())
      .type(type)
      .reason(request.getReason())
      .quantity(request.getQuantity())
      .stockBefore(stockBefore)
      .stockAfter(stockAfter)
      .notes(request.getNotes())
      .createdBy(userId)
      .build();

    StockMovement saved = movementRepository.save(movement);

    log.info("Ajuste de stock registrado: branch={} type={} reason={} qty={}",
      request.getBranchId(), type,
      request.getReason(), request.getQuantity());

    return toMovementResponse(saved);
  }

  // ================================================================
  // TRANSFERENCIA ENTRE SUCURSALES
  // ================================================================

  // ── POST /api/stock/transfers ─────────────────────────────────
  // Transfiere stock de una sucursal a otra.
  // @Transactional garantiza que ambos movimientos
  // ocurren o ninguno — nunca stock perdido.
  //
  // FLUJO:
  //   1. Validar que las sucursales son diferentes
  //   2. Validar product_id XOR variant_id
  //   3. Verificar stock suficiente en origen
  //   4. Generar UUID de referencia para trazabilidad
  //   5. Registrar TRANSFER_OUT en origen
  //   6. Registrar TRANSFER_IN en destino
  @Transactional
  public StockMovementResponse registerTransfer(
    StockTransferRequest request) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    // VALIDACIÓN 1 — sucursales distintas
    if (request.getFromBranchId().equals(request.getToBranchId())) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("transfer.same.branch"), "toBranchId");
    }

    // VALIDACIÓN 2 — product_id XOR variant_id
    validateProductOrVariant(request.getProductId(), request.getVariantId());

    // PASO 1 — Buscar stock en sucursal origen
    Stock stockOrigen = findOrCreateStock(
      request.getFromBranchId(),
      request.getProductId(),
      request.getVariantId(),
      userId);

    // PASO 2 — Validar stock suficiente en origen
    validateSufficientStock(stockOrigen, request.getQuantity());

    // PASO 3 — UUID de referencia para trazabilidad
    // Ambos movimientos (OUT e IN) compartirán este ID
    // Permite buscar la transferencia completa en el historial
    UUID transferRef = UUID.randomUUID();

    // PASO 4 — Registrar TRANSFER_OUT en sucursal origen
    BigDecimal origenBefore = stockOrigen.getQuantity();
    BigDecimal origenAfter = origenBefore.subtract(request.getQuantity());

    stockOrigen.setQuantity(origenAfter);
    stockOrigen.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    stockRepository.save(stockOrigen);

    StockMovement movOut = StockMovement.builder()
      .branchId(request.getFromBranchId())
      .productId(request.getProductId())
      .variantId(request.getVariantId())
      .type(MovementType.OUT)
      .reason(MovementReason.TRANSFER_OUT)
      .quantity(request.getQuantity())
      .stockBefore(origenBefore)
      .stockAfter(origenAfter)
      .referenceId(transferRef)
      .notes(request.getNotes())
      .createdBy(userId)
      .build();
    movementRepository.save(movOut);

    // PASO 5 — Registrar TRANSFER_IN en sucursal destino
    Stock stockDestino = findOrCreateStock(
      request.getToBranchId(),
      request.getProductId(),
      request.getVariantId(),
      userId);

    BigDecimal destinoBefore = stockDestino.getQuantity();
    BigDecimal destinoAfter = destinoBefore.add(request.getQuantity());

    stockDestino.setQuantity(destinoAfter);
    stockDestino.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    stockRepository.save(stockDestino);

    StockMovement movIn = StockMovement.builder()
      .branchId(request.getToBranchId())
      .productId(request.getProductId())
      .variantId(request.getVariantId())
      .type(MovementType.IN)
      .reason(MovementReason.TRANSFER_IN)
      .quantity(request.getQuantity())
      .stockBefore(destinoBefore)
      .stockAfter(destinoAfter)
      .referenceId(transferRef)
      .notes(request.getNotes())
      .createdBy(userId)
      .build();

    StockMovement savedIn = movementRepository.save(movIn);

    log.info("Transferencia registrada: from={} to={} qty={} ref={}",
      request.getFromBranchId(),
      request.getToBranchId(),
      request.getQuantity(), transferRef);

    // Retornamos el movimiento TRANSFER_IN (destino)
    // Es el que confirma que el stock llegó
    return toMovementResponse(savedIn);
  }

  // ================================================================
  // HELPERS PRIVADOS
  // ================================================================

  // ── Resolver tipo según reason ────────────────────────────────
  // REGLA 5: el service decide el type, no el usuario
  // El usuario solo envía la reason — más simple y sin errores
  private MovementType resolveType(MovementReason reason) {
    return switch (reason) {
      case PURCHASE,
           TRANSFER_IN,
           ADJUSTMENT_IN,
           RETURN_CUSTOMER -> MovementType.IN;
      case SALE,
           TRANSFER_OUT,
           ADJUSTMENT_OUT,
           RETURN_SUPPLIER -> MovementType.OUT;
    };
  }

  // ── Buscar o crear registro de stock ──────────────────────────
  // Si ya existe → lo devuelve para actualizarlo
  // Si no existe → crea uno nuevo con quantity = 0
  // El stock se crea automáticamente al primer movimiento
  private Stock findOrCreateStock(
    UUID branchId,
    UUID productId,
    UUID variantId,
    UUID userId) {

    // Buscar según producto o variante
    if (variantId != null) {
      return stockRepository
        .findByBranchIdAndVariantId(branchId, variantId)
        .orElseGet(() -> createNewStock(
          branchId, productId,
          variantId, userId));
    } else {
      return stockRepository
        .findByBranchIdAndProductIdAndVariantIdIsNull(branchId, productId)
        .orElseGet(() -> createNewStock(
          branchId, productId,
          null, userId));
    }
  }

  // ── Crear nuevo registro de stock ─────────────────────────────
  // Solo se llama desde findOrCreateStock
  // Primer ingreso de stock de un producto en una sucursa
  private Stock createNewStock(
    UUID branchId,
    UUID productId,
    UUID variantId,
    UUID userId) {

    Stock newStock = Stock.builder()
      .branchId(branchId)
      .productId(productId)
      .variantId(variantId)
      .quantity(BigDecimal.ZERO)
      .minQuantity(BigDecimal.ZERO)
      .createdBy(userId)
      .build();

    log.info("Creando nuevo registro de stock: " +
        "branch={} product={} variant={}",
      branchId, productId, variantId);

    return stockRepository.save(newStock);
  }

  // ── Validar stock suficiente para salidas ─────────────────────
  // Solo aplica para movimientos OUT
  // quantity del movimiento <= quantity del stock actual
  private void validateSufficientStock(
    Stock stock, BigDecimal requested) {

    if (stock.getQuantity().compareTo(requested) < 0) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("stock.insufficient",
          stock.getQuantity(), requested),
        "quantity");
    }
  }

  // ── Validar exclusividad product_id / variant_id ──────────────
  // Debe enviarse uno de los dos, nunca ambos ni ninguno
  private void validateProductOrVariant(
    UUID productId, UUID variantId) {

    if (productId == null && variantId == null) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("stock.product.or.variant.required"),
        "productId");
    }
    if (productId != null && variantId != null) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("stock.product.and.variant.exclusive"),
        "productId");
    }
  }

  // ── Mapper Stock → StockResponse ─────────────────────────────
  // belowMinimum → campo calculado, no viene de la BD
  // true si quantity <= min_quantity Y min_quantity > 0
  private StockResponse toResponse(Stock stock) {
    boolean belowMinimum =
      stock.getMinQuantity()
        .compareTo(BigDecimal.ZERO) > 0
        && stock.getQuantity()
        .compareTo(stock.getMinQuantity()) <= 0;

    return StockResponse.builder()
      .id(stock.getId())
      .branchId(stock.getBranchId())
      .productId(stock.getProductId())
      .variantId(stock.getVariantId())
      .quantity(stock.getQuantity())
      .minQuantity(stock.getMinQuantity())
      .maxQuantity(stock.getMaxQuantity())
      .belowMinimum(belowMinimum)
      .lastUpdatedAt(stock.getLastUpdatedAt())
      .createdAt(stock.getCreatedAt())
      .build();
  }

  // ── Mapper StockMovement → StockMovementResponse ─────────────
  private StockMovementResponse toMovementResponse(
    StockMovement movement) {

    return StockMovementResponse.builder()
      .id(movement.getId())
      .branchId(movement.getBranchId())
      .productId(movement.getProductId())
      .variantId(movement.getVariantId())
      .type(movement.getType())
      .reason(movement.getReason())
      .quantity(movement.getQuantity())
      .stockBefore(movement.getStockBefore())
      .stockAfter(movement.getStockAfter())
      .referenceId(movement.getReferenceId())
      .notes(movement.getNotes())
      .createdBy(movement.getCreatedBy())
      .createdAt(movement.getCreatedAt())
      .build();
  }


}
