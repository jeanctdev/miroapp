package com.miroapp.product.service;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.product.config.SecurityUtils;
import com.miroapp.product.config.TenantContext;
import com.miroapp.product.dto.CreateVariantRequest;
import com.miroapp.product.dto.ProductVariantResponse;
import com.miroapp.product.dto.UpdateVariantRequest;
import com.miroapp.product.entity.Product;
import com.miroapp.product.entity.ProductType;
import com.miroapp.product.entity.ProductVariant;
import com.miroapp.product.repository.ProductRepository;
import com.miroapp.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// =====================================================================
// ProductVariantService — lógica de negocio para variantes
// =====================================================================
// REGLAS DE NEGOCIO CRÍTICAS:
//
// REGLA 1 — Solo PHYSICAL con hasVariants=true:
//   No se crean variantes para SERVICE ni DIGITAL.
//   No tiene sentido tener "tallas" de una consulta médica.
//   No se crean variantes si hasVariants=false en el padre.
//   Si el dueño quiere variantes → primero activa hasVariants.
//
// REGLA 2 — SKU y barcode únicos por tenant:
//   Validamos ANTES del constraint de BD para dar mensaje claro.
//   Incluye verificar que no exista en products NI en variants.
//
// REGLA 3 — Precio resuelto (price null → hereda del padre):
//   El POS siempre necesita un precio real para cobrar.
//   Si variant.price es null → usa product.basePrice.
//   El frontend recibe resolvedPrice → nunca ve null.
//
// REGLA 4 — Soft delete en cascada:
//   Si se elimina el producto padre → todas sus variantes
//   activas también se marcan con deleted_at.
//   Esta lógica vive aquí para que ProductService la llame.
//
// REGLA 5 — SET search_path al inicio de CADA método:
//   Multi-tenancy — sin esto JPA opera en schema incorrecto.
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

  private final ProductVariantRepository variantRepository;
  private final ProductRepository productRepository;
  private final TenantContext tenantContext;
  private final SecurityUtils securityUtils;
  private final MessageSource messageSource;

  // ── Helper para mensajes ──────────────────────────────────────
  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code, args, code, LocaleContextHolder.getLocale()
    );
  }

  // ── Listar variantes de un producto ───────────────────────────
  // Usado en el POS cuando el cajero selecciona un producto
  // con hasVariants=true:
  //   Cajero toca "Collar Nylon" → aparecen Talla S, M, L, XL
  // Ordenadas por sort_order — el dueño decide el orden visual.
  @Transactional(readOnly = true)
  public Page<ProductVariantResponse> findByProduct(
    UUID productId, Pageable pageable) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    // Verificar que el producto padre existe
    Product product = findProductOrThrow(productId);
    return variantRepository
      .findAllByProductIdAndActiveTrue(productId, pageable)
      .map(variant -> toResponse(variant, product));
  }

  // ── Obtener una variante por ID ───────────────────────────────
  @Transactional(readOnly = true)
  public ProductVariantResponse findById(
    UUID productId, UUID variantId) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    Product product = findProductOrThrow(productId);
    ProductVariant variant = findVariantOrThrow(variantId);

    // Verificar que la variante pertenece al producto indicado
    // Evita que alguien consulte una variante de otro producto
    // usando un productId incorrecto en la URL
    validateVariantBelongsToProduct(variant, productId);

    return toResponse(variant, product);
  }

  // ── Buscar variante por SKU — escritura en el POS ─────────────
  // El cajero escribe el SKU directamente en el teclado.
  // Retorna UN solo resultado — el SKU es único por tenant.
  @Transactional(readOnly = true)
  public ProductVariantResponse findBySku(String sku) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    ProductVariant variant = variantRepository
      .findBySkuAndActiveTrue(sku)
      .orElseThrow(() -> new ResourceNotFoundException(
        getMessage("variant.not.found", sku)
      ));

    Product product = findProductOrThrow(variant.getProductId());
    return toResponse(variant, product);
  }

  // ── Buscar variante por barcode — scanner del POS ─────────────
  // El cajero pasa el producto por el lector de código de barras.
  @Transactional(readOnly = true)
  public ProductVariantResponse findByBarcode(String barcode) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());

    ProductVariant variant = variantRepository
      .findByBarcodeAndActiveTrue(barcode)
      .orElseThrow(() -> new ResourceNotFoundException(
        getMessage("variant.not.found", barcode)
      ));

    Product product = findProductOrThrow(variant.getProductId());
    return toResponse(variant, product);
  }

  // ── Crear variante ────────────────────────────────────────────
  @Transactional
  public ProductVariantResponse create(
    UUID productId, CreateVariantRequest request) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    // VALIDACIÓN 1 — Producto padre existe
    Product product = findProductOrThrow(productId);

    // VALIDACIÓN 2 — Solo PHYSICAL puede tener variantes
    // SERVICE y DIGITAL no tienen sentido con variantes
    if (product.getType() != ProductType.PHYSICAL) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("variant.product.not.physical"),
        "productId"
      );
    }

    // VALIDACIÓN 3 — El producto debe tener hasVariants=true
    // Si el dueño no activó las variantes en el producto
    // primero debe hacer PUT /api/products/{id} con hasVariants=true
    if (!Boolean.TRUE.equals(product.getHasVariants())) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("variant.product.not.enabled"),
        "productId"
      );
    }

    // VALIDACIÓN 4 — SKU único si se envió
    // Verificamos en AMBAS tablas: products y product_variants
    // Un SKU no puede existir en ninguna de las dos
    if (StringUtils.hasText(request.getSku())) {
      if (variantRepository.existsBySku(request.getSku())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.sku.duplicate",
            request.getSku()),
          "sku"
        );
      }
      // También verificar en la tabla products
      if (productRepository.existsBySku(request.getSku())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.sku.duplicate",
            request.getSku()),
          "sku"
        );
      }
    }

    // VALIDACIÓN 5 — Barcode único si se envió
    if (StringUtils.hasText(request.getBarcode())) {
      if (variantRepository.existsByBarcode(
        request.getBarcode())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.barcode.duplicate",
            request.getBarcode()),
          "barcode"
        );
      }
      if (productRepository.existsByBarcode(
        request.getBarcode())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.barcode.duplicate",
            request.getBarcode()),
          "barcode"
        );
      }
    }

    ProductVariant variant = ProductVariant.builder()
      .productId(productId)
      .name(request.getName().trim())
      .sku(StringUtils.hasText(request.getSku())
        ? request.getSku().trim().toUpperCase() : null)
      .barcode(StringUtils.hasText(request.getBarcode())
        ? request.getBarcode().trim() : null)
      .price(request.getPrice())
      .cost(request.getCost())
      .attributes(StringUtils.hasText(request.getAttributes())
        ? request.getAttributes().trim() : "{}")
      .imageUrl(request.getImageUrl())
      .sortOrder(request.getSortOrder() != null
        ? request.getSortOrder() : (short) 0)
      .active(true)
      .createdBy(userId)
      .build();

    ProductVariant saved = variantRepository.save(variant);

    log.info("Variante creada: id={} sku={} producto={} tenant={}",
      saved.getId(), saved.getSku(), productId,
      securityUtils.getCurrentTenantSlug());

    return toResponse(saved, product);
  }

  // ── Actualizar variante ───────────────────────────────────────
  @Transactional
  public ProductVariantResponse update(
    UUID productId, UUID variantId,
    UpdateVariantRequest request) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    Product product = findProductOrThrow(productId);
    ProductVariant variant = findVariantOrThrow(variantId);
    validateVariantBelongsToProduct(variant, productId);

    // Validar SKU único si cambió
    if (StringUtils.hasText(request.getSku())
      && !request.getSku().equalsIgnoreCase(
      variant.getSku())) {

      if (variantRepository.existsBySku(request.getSku())
        || productRepository.existsBySku(
        request.getSku())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.sku.duplicate",
            request.getSku()),
          "sku"
        );
      }
    }

    // Validar barcode único si cambió
    if (StringUtils.hasText(request.getBarcode())
      && !request.getBarcode().equals(
      variant.getBarcode())) {

      if (variantRepository.existsByBarcode(
        request.getBarcode())
        || productRepository.existsByBarcode(
        request.getBarcode())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("variant.barcode.duplicate",
            request.getBarcode()),
          "barcode"
        );
      }
    }

    // Aplicar solo los campos enviados — patch-like behavior
    if (StringUtils.hasText(request.getName()))
      variant.setName(request.getName().trim());
    if (StringUtils.hasText(request.getSku()))
      variant.setSku(request.getSku().trim().toUpperCase());
    if (StringUtils.hasText(request.getBarcode()))
      variant.setBarcode(request.getBarcode().trim());
    if (request.getPrice() != null)
      variant.setPrice(request.getPrice());
    if (request.getCost() != null)
      variant.setCost(request.getCost());
    if (StringUtils.hasText(request.getAttributes()))
      variant.setAttributes(request.getAttributes().trim());
    if (request.getImageUrl() != null)
      variant.setImageUrl(request.getImageUrl());
    if (request.getSortOrder() != null)
      variant.setSortOrder(request.getSortOrder());
    if (request.getActive() != null)
      variant.setActive(request.getActive());

    // Actualizar updatedAt manualmente — convención MIRO
    variant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

    ProductVariant updated = variantRepository.save(variant);

    log.info("Variante actualizada: id={} por usuario={}",
      variantId, userId);

    return toResponse(updated, product);
  }

  // ── Eliminar variante (soft delete) ───────────────────────────
  @Transactional
  public void delete(UUID productId, UUID variantId) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    findProductOrThrow(productId);
    ProductVariant variant = findVariantOrThrow(variantId);
    validateVariantBelongsToProduct(variant, productId);

    variant.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    variant.setActive(false);
    variantRepository.save(variant);

    log.info("Variante eliminada (soft): id={} por usuario={}",
      variantId, userId);
  }

  // ── Soft delete en cascada ────────────────────────────────────
  // ProductService llama este método cuando elimina un producto padre.
  // Todas las variantes activas del producto se marcan eliminadas.
  // Convención MIRO: los datos son inmutables — solo soft delete.
  @Transactional
  public void deleteAllByProduct(UUID productId) {

    tenantContext.set(securityUtils.getCurrentTenantSlug());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    variantRepository
      .findAllByProductIdAndActiveTrue(
        productId, Pageable.unpaged())
      .forEach(variant -> {
        variant.setDeletedAt(now);
        variant.setActive(false);
        variantRepository.save(variant);
      });

    log.info("Variantes eliminadas en cascada para producto={}",
      productId);
  }

  // ── Helper — buscar producto o lanzar 404 ─────────────────────
  private Product findProductOrThrow(UUID productId) {
    return productRepository.findById(productId)
      .orElseThrow(() -> new ResourceNotFoundException(
        getMessage("product.not.found", productId)
      ));
  }

  // ── Helper — buscar variante o lanzar 404 ─────────────────────
  private ProductVariant findVariantOrThrow(UUID variantId) {
    return variantRepository.findById(variantId)
      .orElseThrow(() -> new ResourceNotFoundException(
        getMessage("variant.not.found", variantId)
      ));
  }

  // ── Helper — validar que variante pertenece al producto ───────
  // Seguridad: evita acceder a variantes de otro producto
  // usando una URL manipulada como:
  //   GET /api/products/PRODUCTO_A/variants/VARIANTE_DE_B
  private void validateVariantBelongsToProduct(
    ProductVariant variant, UUID productId) {
    if (!variant.getProductId().equals(productId)) {
      throw new ResourceNotFoundException(
        getMessage("variant.not.found", variant.getId())
      );
    }
  }

  // ── Mapper — entidad a DTO de respuesta ───────────────────────
  // Data enrichment:
  //   productName   → nombre del padre (evita segunda llamada)
  //   resolvedPrice → precio real del POS (variante o padre)
  //   resolvedCost  → costo real para margen
  //
  // El POS SIEMPRE recibe resolvedPrice con valor — nunca null.
  private ProductVariantResponse toResponse(
    ProductVariant variant, Product product) {

    // Precio resuelto:
    //   Si la variante tiene precio propio → lo usa
    //   Si no → hereda el precio del producto padre
    BigDecimal resolvedPrice = variant.getPrice() != null
      ? variant.getPrice()
      : product.getBasePrice();

    // Costo resuelto — igual lógica que el precio
    BigDecimal resolvedCost = variant.getCost() != null
      ? variant.getCost()
      : product.getBaseCost();

    return ProductVariantResponse.builder()
      .id(variant.getId())
      .productId(variant.getProductId())
      .productName(product.getName())
      .name(variant.getName())
      .sku(variant.getSku())
      .barcode(variant.getBarcode())
      .price(variant.getPrice())
      .cost(variant.getCost())
      .resolvedPrice(resolvedPrice)
      .resolvedCost(resolvedCost)
      .attributes(variant.getAttributes())
      .imageUrl(variant.getImageUrl())
      .sortOrder(variant.getSortOrder())
      .active(variant.getActive())
      .createdAt(variant.getCreatedAt())
      .updatedAt(variant.getUpdatedAt())
      .build();
  }

}
