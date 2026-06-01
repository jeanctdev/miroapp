package com.miroapp.product.service;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.product.config.SecurityUtils;
import com.miroapp.product.config.TenantContext;
import com.miroapp.product.dto.*;
import com.miroapp.product.entity.Category;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// =====================================================================
// CategoryService — lógica de negocio para categorías
// =====================================================================
// Convenciones MIRO:
//   @Transactional en TODOS los métodos que escriben en BD
//   getMessage() para TODOS los mensajes — sin hardcodear
//   Soft delete — nunca DELETE físico
//   UUID del usuario viene del SecurityContext (propagado por gateway)
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository   productRepository;
    private final MessageSource        messageSource;
    private final TenantContext tenantContext;
    private final SecurityUtils securityUtils;

    // ── Helper para mensajes ──────────────────────────────────────
    private String getMessage(String code, Object... args) {
        return messageSource.getMessage(
                code, args, code, LocaleContextHolder.getLocale()
        );
    }

    // ── Listar categorías paginadas ───────────────────────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(Pageable pageable) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());
      return categoryRepository
        .findAllByActiveTrue(pageable)
        .map(this::toResponse);
    }

    // ── Listar categorías raíz (sin padre) ───────────────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findRoots(Pageable pageable) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());
      return categoryRepository
        .findAllByParentIdIsNullAndActiveTrue(pageable)
        .map(this::toResponse);
    }

    // ── Listar subcategorías de una categoría padre ───────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findByParent(
            UUID parentId, Pageable pageable) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());

        // Verificar que el padre existe
        findCategoryOrThrow(parentId);

        return categoryRepository
                .findAllByParentIdAndActiveTrue(parentId, pageable)
                .map(this::toResponse);
    }

    // ── Buscar por nombre ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> search(
            String name, Pageable pageable) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());

      return categoryRepository
                .findAllByNameContainingIgnoreCaseAndActiveTrue(
                        name, pageable)
                .map(this::toResponse);
    }

    // ── Obtener una categoría por ID ──────────────────────────────
    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());
      return toResponse(findCategoryOrThrow(id));
    }

  // ── GET /tree — Árbol completo anidado ────────────────────────
  // ¿Por qué un endpoint /tree y no solo /roots?
  //   /roots devuelve solo el primer nivel → el frontend
  //   necesita hacer N llamadas para construir el árbol completo.
  //   /tree devuelve TODO el árbol en una sola llamada.
  //   El frontend puede construir el menú de navegación sin más
  //   requests. Mucho más eficiente para el POS.
  //
  // Algoritmo:
  //   1. Traer todas las categorías raíz (parentId IS NULL)
  //   2. Para cada raíz → buscar sus hijos recursivamente
  //   3. Construir CategoryResponse con campo children lleno
  //   4. Los nodos hoja tienen children = [] (lista vacía)
  @Transactional(readOnly = true)
  public List<CategoryResponse> getTree() {
    tenantContext.set(securityUtils.getCurrentTenantSlug());

    // Traer todas las raíces ordenadas por sortOrder
    List<Category> roots = categoryRepository
      .findAllByParentIdIsNullAndActiveTrueOrderBySortOrderAsc();

    // Para cada raíz construir el subárbol recursivamente
    return roots.stream()
      .map(this::buildSubTree)
      .collect(Collectors.toList());
  }

    // ── Crear categoría ───────────────────────────────────────────
    @Transactional
    public CategoryResponse create(
            CreateCategoryRequest request) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());
      UUID userId = securityUtils.getCurrentUserId();

      // Validar nombre duplicado
      if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("category.name.duplicate", request.getName()),
          "name"
        );
      }

      // Validar que el padre existe si se envió
      if (request.getParentId() != null) {
        findCategoryOrThrow(request.getParentId());
      }

      Category category = Category.builder()
        .name(request.getName())
        .description(request.getDescription())
        .parentId(request.getParentId())
        .sortOrder(request.getSortOrder() != null
          ? request.getSortOrder() : (short) 0)
        .active(true)
        .createdBy(userId)
        .build();

      Category saved = categoryRepository.save(category);
      log.info("Categoría creada: {} por usuario: {}", saved.getId(), userId);
      return toResponse(saved);
    }

  @Transactional
  public CategoryResponse update(
    UUID id, UpdateCategoryRequest request) {
    tenantContext.set(securityUtils.getCurrentTenantSlug());
    UUID userId = securityUtils.getCurrentUserId();

    Category category = findCategoryOrThrow(id);

    // VALIDACIÓN CICLOS — solo si parentId cambió
    if (request.getParentId() != null
      && !request.getParentId().equals(category.getParentId())) {

      // No puede ser su propio padre
      if (request.getParentId().equals(id)) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("category.self.parent"),
          "parentId"
        );
      }

      // Verificar que el nuevo padre existe
      findCategoryOrThrow(request.getParentId());

      // Verificar que no crea un ciclo
      if (isDescendant(id, request.getParentId())) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("category.cycle.detected"),
          "parentId"
        );
      }

      category.setParentId(request.getParentId());
    }

    // VALIDACIÓN NOMBRE DUPLICADO — solo si cambió
    if (request.getName() != null
      && !request.getName().equalsIgnoreCase(category.getName())
      && categoryRepository.existsByNameIgnoreCase(
      request.getName())) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("category.name.duplicate",
          request.getName()),
        "name"
      );
    }

    // VALIDACIÓN DESACTIVAR — solo si se intenta poner active=false
    if (Boolean.FALSE.equals(request.getActive())) {
      if (categoryRepository.existsByParentIdAndActiveTrue(id)) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("category.has.children"),
          "active"
        );
      }
      if (productRepository.existsByCategoryIdAndActiveTrue(id)) {
        throw new BusinessException(
          ErrorCodes.VALIDATION_ERROR,
          getMessage("category.has.products"),
          "active"
        );
      }
    }

    // APLICAR CAMBIOS — solo los campos que vienen en el request
    if (request.getName()        != null)
      category.setName(request.getName().trim());
    if (request.getDescription() != null)
      category.setDescription(request.getDescription().trim());
    if (request.getSortOrder()   != null)
      category.setSortOrder(request.getSortOrder());
    if (request.getActive()      != null)
      category.setActive(request.getActive());

    // parentId ya fue aplicado arriba en el bloque de ciclos
    // NO repetir aquí

    category.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    Category updated = categoryRepository.save(category);

    log.info("Categoria actualizada: id={} por usuario={}",
      id, userId);

    return toResponse(updated);
  }

    // ── Eliminar categoría (soft delete) ──────────────────────────
    @Transactional
    public void delete(UUID id) {
      tenantContext.set(securityUtils.getCurrentTenantSlug());
      UUID userId = securityUtils.getCurrentUserId();

        Category category = findCategoryOrThrow(id);

        // No eliminar si tiene subcategorías activas
        if (categoryRepository.existsByParentIdAndActiveTrue(id)) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("category.has.subcategories")
            );
        }

        // No eliminar si tiene productos activos
        if (productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("category.has.products")
            );
        }

        // Soft delete — nunca DELETE físico
        category.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC)); // ← ZoneOffset.UTC
        category.setActive(false);
        categoryRepository.save(category);

        log.info("Categoría eliminada (soft): {} por usuario: {}", id, userId);
    }

    // ── Helper — buscar o lanzar 404 ─────────────────────────────
    private Category findCategoryOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        getMessage("category.not.found", id)
                ));
    }

    // ── Mapper — entidad a DTO de respuesta ───────────────────────
    private CategoryResponse toResponse(Category category) {

        // Si tiene padre, buscamos el nombre del padre
        String parentName = null;
        if (category.getParentId() != null) {
            parentName = categoryRepository
                    .findById(category.getParentId())
                    .map(Category::getName)
                    .orElse(null);
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .parentName(parentName)
                .sortOrder(category.getSortOrder())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

  // ── Helper: construir subárbol recursivo ──────────────────────
  // Recibe una categoría y devuelve su CategoryResponse
  // con todos sus hijos anidados recursivamente.
  //
  // Ejemplo de resultado para "Alimentos":
  //   CategoryResponse {
  //     id: "9c2c...",
  //     name: "Alimentos",
  //     children: [
  //       CategoryResponse {
  //         id: "b069...",
  //         name: "Alimento Seco",
  //         children: []  ← nodo hoja
  //       },
  //       CategoryResponse {
  //         id: "9b10...",
  //         name: "Alimento Humedo",
  //         children: []
  //       }
  //     ]
  //   }
  private CategoryResponse buildSubTree(Category category) {
    // Buscar todos los hijos directos ordenados por sortOrder
    List<Category> children = categoryRepository
      .findAllByParentIdAndActiveTrueOrderBySortOrderAsc(
        category.getId());

    // Para cada hijo → construir su subárbol recursivamente
    List<CategoryResponse> childResponses = children.stream()
      .map(this::buildSubTree)
      .collect(Collectors.toList());

    // Construir la respuesta con los hijos anidados
    return CategoryResponse.builder()
      .id(category.getId())
      .parentId(category.getParentId())
      .name(category.getName())
      .description(category.getDescription())
      .sortOrder(category.getSortOrder())
      .active(category.getActive())
      .createdAt(category.getCreatedAt())
      .updatedAt(category.getUpdatedAt())
      .children(childResponses)
      .build();
  }

  // ── Helper: detectar ciclos en el árbol ───────────────────────
  // Verifica si `potentialDescendantId` es un descendiente de
  // `ancestorId` para evitar ciclos al cambiar el parentId.
  //
  // ¿Por qué es necesario?
  //   Sin esta validación podría ocurrir:
  //     Alimentos (raíz)
  //       └── Alimento Seco (hijo)
  //
  //   Si intentamos poner Alimentos como hijo de Alimento Seco:
  //     Alimento Seco ← intenta ser padre de Alimentos
  //       └── Alimentos ← intenta ser padre de Alimento Seco
  //   Esto crea un ciclo infinito → el árbol se destruye
  //
  // Algoritmo: recorre hacia ABAJO desde ancestorId
  //   Si en algún nivel encuentra potentialDescendantId → hay ciclo
  //   Si llega a nodos hoja sin encontrarlo → no hay ciclo
  private boolean isDescendant(UUID ancestorId,
                               UUID potentialDescendantId) {
    // Buscar todos los hijos directos del ancestro
    List<Category> children = categoryRepository
      .findAllByParentIdAndActiveTrueOrderBySortOrderAsc(
        ancestorId);

    for (Category child : children) {
      // Si el hijo ES el potencial descendiente → es ciclo
      if (child.getId().equals(potentialDescendantId)) {
        return true;
      }
      // Buscar recursivamente en los hijos del hijo
      if (isDescendant(child.getId(),
        potentialDescendantId)) {
        return true;
      }
    }

    // No encontró ciclo en ningún nivel
    return false;
  }
}