package com.miroapp.product.service;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.product.config.SecurityUtils;
import com.miroapp.product.config.TenantContext;
import com.miroapp.product.dto.CreateProductRequest;
import com.miroapp.product.dto.ProductResponse;
import com.miroapp.product.dto.UpdateProductRequest;
import com.miroapp.product.entity.Product;
import com.miroapp.product.entity.ProductType;
import com.miroapp.product.repository.CategoryRepository;
import com.miroapp.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// ProductService — lógica de negocio del catálogo de productos
// =====================================================================
// REGLAS DE NEGOCIO CRÍTICAS:
//
// REGLA 1 — type es INMUTABLE después de creado:
//   Razón: ventas e inventario histórico dependen del type.
//   Una venta PHYSICAL descontó stock — cambiar a SERVICE
//   rompería la auditoría del inventario. Es un dato estructural.
//   Solución: UpdateProductRequest NO tiene campo type.
//
// REGLA 2 — track_stock forzado según el type:
//   SERVICE y DIGITAL → track_stock = false SIEMPRE.
//   PHYSICAL → track_stock puede ser true o false.
//   Si el usuario manda track_stock=true para SERVICE → corregimos.
//   Razón: inventario solo aplica a bienes físicos.
//
// REGLA 3 — SKU y barcode únicos por tenant:
//   UNIQUE en BD garantiza integridad a nivel de datos.
//   Validamos antes de insertar para dar mensaje claro al usuario
//   en vez de dejar fallar el constraint con un error genérico.
//
// REGLA 4 — Soft delete SIEMPRE:
//   deleted_at = now() + active = false
//   NUNCA productRepository.delete() físico.
//   Un producto eliminado puede seguir en ventas históricas.
//   Los datos son inmutables — la auditoría es sagrada.
//
// REGLA 5 — SET search_path al inicio de CADA método:
//   Multi-tenancy: cada empresa tiene su propio schema.
//   Sin esto JPA operaría en el schema público o equivocado.
//   @Transactional garantiza que el search_path persiste
//   durante toda la transacción — una sola conexión.
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantContext tenantContext;
    private final SecurityUtils securityUtils;
    private final MessageSource messageSource;

    // ── Helper para mensajes ──────────────────────────────────────
    // Convención MIRO: NUNCA strings hardcodeados.
    // Todos los mensajes vienen de messages.properties.
    private String getMessage(String code, Object... args) {
        return messageSource.getMessage(
                code, args, code, LocaleContextHolder.getLocale()
        );
    }

    // ── Listar todos los productos activos ────────────────────────
    // readOnly=true → Hibernate desactiva dirty checking → más rápido.
    // @Transactional garantiza una sola conexión → search_path persiste.
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        //detectar el tenant a buscar
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        //listar
        return productRepository
                .findAllByActiveTrue(pageable)
                .map(this::toResponse);
    }

    // ── Filtrar por tipo ──────────────────────────────────────────
    // El frontend puede filtrar el catálogo por PHYSICAL, SERVICE o DIGITAL.
    // Útil para el POS donde el cajero quiere ver solo servicios o productos.
    @Transactional(readOnly = true)
    public Page<ProductResponse> findByType(
            ProductType type, Pageable pageable) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        return productRepository
                .findAllByTypeAndActiveTrue(type, pageable)
                .map(this::toResponse);
    }

    // ── Filtrar por categoría ─────────────────────────────────────
    // Cuando el cajero navega el árbol de categorías en el POS.
    @Transactional(readOnly = true)
    public Page<ProductResponse> findByCategory(
            UUID categoryId, Pageable pageable) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());

        // Verificar que la categoría existe antes de buscar
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        getMessage("category.not.found", categoryId)
                ));

        return productRepository
                .findAllByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::toResponse);
    }

    // ── Búsqueda combinada ────────────────────────────────────────
    // El frontend puede combinar nombre + categoría en una sola llamada.
    // El service decide qué query usar según los parámetros recibidos.
    // Así el controller es simple y el service centraliza la lógica.
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(
            String name, UUID categoryId, Pageable pageable) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());

        // Nombre + categoría
        if (StringUtils.hasText(name) && categoryId != null) {
            return productRepository
                    .findAllByNameContainingIgnoreCaseAndCategoryIdAndActiveTrue(
                            name, categoryId, pageable)
                    .map(this::toResponse);
        }

        // Solo nombre
        if (StringUtils.hasText(name)) {
            return productRepository
                    .findAllByNameContainingIgnoreCaseAndActiveTrue(
                            name, pageable)
                    .map(this::toResponse);
        }

        // Solo categoría
        if (categoryId != null) {
            return productRepository
                    .findAllByCategoryIdAndActiveTrue(categoryId, pageable)
                    .map(this::toResponse);
        }

        // Sin filtros → todos los activos
        return productRepository
                .findAllByActiveTrue(pageable)
                .map(this::toResponse);
    }

    // ── Buscar por SKU — búsqueda directa en el POS ───────────────
    // El cajero escribe el SKU manualmente en el POS para agregar
    // un producto a la venta sin necesidad de buscarlo por nombre.
    @Transactional(readOnly = true)
    public ProductResponse findBySku(String sku) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        return productRepository.findBySkuAndActiveTrue(sku)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        getMessage("product.not.found", sku)
                ));
    }

    // ── Buscar por barcode — scanner del POS ──────────────────────
    // El cajero escanea el código de barras con el lector.
    // Es la forma más rápida de agregar un producto en el POS.
    // Solo aplica a productos PHYSICAL.
    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        return productRepository.findByBarcodeAndActiveTrue(barcode)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        getMessage("product.not.found", barcode)
                ));
    }

    // ── Obtener un producto por ID ────────────────────────────────
    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        return toResponse(findProductOrThrow(id));
    }

    // ── Crear producto ────────────────────────────────────────────
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        UUID userId = securityUtils.getCurrentUserId();

        // VALIDACIÓN 1: SKU único si se envió
        // Validamos antes de insertar para dar mensaje claro
        // en vez de dejar fallar el UNIQUE constraint de la BD
        if (StringUtils.hasText(request.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("product.sku.duplicate", request.getSku()),
                    "sku"
            );
        }

        // VALIDACIÓN 2: barcode único si se envió
        if (StringUtils.hasText(request.getBarcode())
                && productRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("product.barcode.duplicate",
                            request.getBarcode()),
                    "barcode"
            );
        }

        // VALIDACIÓN 3: categoría existe si se envió
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            getMessage("category.not.found",
                                    request.getCategoryId())
                    ));
        }

        // REGLA DE NEGOCIO: track_stock según el type
        // SERVICE y DIGITAL → false SIEMPRE
        // PHYSICAL → lo que pidió el usuario, por defecto true
        boolean trackStock = resolveTrackStock(
                request.getType(), request.getTrackStock()
        );

        Product product = Product.builder()
                .categoryId(request.getCategoryId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .type(request.getType())
                .sku(StringUtils.hasText(request.getSku())
                        ? request.getSku().trim().toUpperCase() : null)
                .barcode(StringUtils.hasText(request.getBarcode())
                        ? request.getBarcode().trim() : null)
                .basePrice(request.getBasePrice())
                .baseCost(request.getBaseCost())
                .taxTypeId(request.getTaxTypeId())
                .unit(StringUtils.hasText(request.getUnit())
                        ? request.getUnit().trim() : "unidad")
                .hasVariants(request.getHasVariants() != null
                        ? request.getHasVariants() : false)
                .trackStock(trackStock)
                .imageUrl(request.getImageUrl())
                .active(true)
                .createdBy(userId)
                .build();

        Product saved = productRepository.save(product);

        log.info("Producto creado: id={} nombre={} tipo={} tenant={}",
                saved.getId(), saved.getName(), saved.getType(),
                securityUtils.getCurrentTenantSlug());

        return toResponse(saved);
    }


    // ── Actualizar producto ───────────────────────────────────────
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        UUID userId = securityUtils.getCurrentUserId();

        Product product = findProductOrThrow(id);

        // VALIDACIÓN 1: SKU único si cambió
        if (StringUtils.hasText(request.getSku())
                && !request.getSku().equalsIgnoreCase(product.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("product.sku.duplicate", request.getSku()),
                    "sku"
            );
        }

        // VALIDACIÓN 2: barcode único si cambió
        if (StringUtils.hasText(request.getBarcode())
                && !request.getBarcode().equals(product.getBarcode())
                && productRepository.existsByBarcode(request.getBarcode())) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("product.barcode.duplicate",
                            request.getBarcode()),
                    "barcode"
            );
        }

        // VALIDACIÓN 3: categoría existe si cambió
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            getMessage("category.not.found",
                                    request.getCategoryId())
                    ));
        }

        // Aplicar solo los campos enviados en el request
        // Si el campo es null → no se toca → conserva el valor actual
        if (request.getCategoryId() != null)
            product.setCategoryId(request.getCategoryId());
        if (StringUtils.hasText(request.getName()))
            product.setName(request.getName().trim());
        if (request.getDescription() != null)
            product.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getSku()))
            product.setSku(request.getSku().trim().toUpperCase());
        if (StringUtils.hasText(request.getBarcode()))
            product.setBarcode(request.getBarcode().trim());
        if (request.getBasePrice() != null)
            product.setBasePrice(request.getBasePrice());
        if (request.getBaseCost() != null)
            product.setBaseCost(request.getBaseCost());
        if (request.getTaxTypeId() != null)
            product.setTaxTypeId(request.getTaxTypeId());
        if (StringUtils.hasText(request.getUnit()))
            product.setUnit(request.getUnit().trim());
        if (request.getHasVariants() != null)
            product.setHasVariants(request.getHasVariants());
        if (request.getImageUrl() != null)
            product.setImageUrl(request.getImageUrl());
        if (request.getActive() != null)
            product.setActive(request.getActive());

        // REGLA DE NEGOCIO: track_stock respeta el type actual
        // El type no cambia — pero track_stock puede cambiar
        // solo si el usuario lo envió explícitamente
        if (request.getTrackStock() != null) {
            product.setTrackStock(
                    resolveTrackStock(product.getType(),
                            request.getTrackStock())
            );
        }

        Product updated = productRepository.save(product);

        log.info("Producto actualizado: id={} por usuario={}",
                id, userId);

        return toResponse(updated);
    }

    // ── Eliminar producto (soft delete) ───────────────────────────
    // NUNCA DELETE físico — los datos son inmutables.
    // Un producto eliminado puede seguir en ventas históricas.
    // deleted_at es la marca de eliminación que @SQLRestriction filtra.
    @Transactional
    public void delete(UUID id) {
        tenantContext.set(securityUtils.getCurrentTenantSlug());
        UUID userId = securityUtils.getCurrentUserId();
        Product product = findProductOrThrow(id);
        product.setDeletedAt(OffsetDateTime.now());
        product.setActive(false);
        productRepository.save(product);
        log.info("Producto eliminado (soft): id={} por usuario={}", id, userId);
    }

    // ── REGLA: resolver track_stock según type ────────────────────
    // Método privado para centralizar esta regla de negocio.
    // Si en el futuro el comportamiento cambia → se cambia aquí.
    // No hay lógica duplicada en create() y update().
    private boolean resolveTrackStock(
            ProductType type, Boolean requested) {
        // SERVICE y DIGITAL nunca controlan stock
        // Sin importar lo que pidió el usuario
        if (type == ProductType.SERVICE || type == ProductType.DIGITAL) {
            return false;
        }
        // PHYSICAL: respeta lo que pidió el usuario
        // Si no lo especificó → true por defecto
        return requested != null ? requested : true;
    }

    // ── Helper — buscar producto o lanzar 404 ─────────────────────
    // Centraliza el mensaje de error — sin duplicar en cada método.
    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        getMessage("product.not.found", id)
                ));
    }

    // ── Mapper — entidad a DTO de respuesta ───────────────────────
    // Data enrichment: incluimos categoryName para evitar
    // que el frontend haga una segunda llamada a /api/categories/{id}.
    // Solo hacemos la query si el producto tiene categoría asignada.
    private ProductResponse toResponse(Product product) {

        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository
                    .findById(product.getCategoryId())
                    .map(c -> c.getName())
                    .orElse(null);
        }

        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .name(product.getName())
                .description(product.getDescription())
                .type(product.getType())
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .basePrice(product.getBasePrice())
                .baseCost(product.getBaseCost())
                .taxTypeId(product.getTaxTypeId())
                .unit(product.getUnit())
                .hasVariants(product.getHasVariants())
                .trackStock(product.getTrackStock())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

}
