package com.miroapp.product.service;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
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
import java.util.UUID;

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

    // ── Helper para mensajes ──────────────────────────────────────
    private String getMessage(String code, Object... args) {
        return messageSource.getMessage(
                code, args, code, LocaleContextHolder.getLocale()
        );
    }

    // ── Listar categorías paginadas ───────────────────────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(Pageable pageable) {
        return categoryRepository
                .findAllByActiveTrue(pageable)
                .map(this::toResponse);
    }

    // ── Listar categorías raíz (sin padre) ───────────────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findRoots(Pageable pageable) {
        return categoryRepository
                .findAllByParentIdIsNullAndActiveTrue(pageable)
                .map(this::toResponse);
    }

    // ── Listar subcategorías de una categoría padre ───────────────
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findByParent(
            UUID parentId, Pageable pageable) {

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
        return categoryRepository
                .findAllByNameContainingIgnoreCaseAndActiveTrue(
                        name, pageable)
                .map(this::toResponse);
    }

    // ── Obtener una categoría por ID ──────────────────────────────
    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return toResponse(findCategoryOrThrow(id));
    }

    // ── Crear categoría ───────────────────────────────────────────
    @Transactional
    public CategoryResponse create(
            CreateCategoryRequest request, UUID userId) {

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

    // ── Actualizar categoría ──────────────────────────────────────
    @Transactional
    public CategoryResponse update(
            UUID id, UpdateCategoryRequest request, UUID userId) {

        Category category = findCategoryOrThrow(id);

        // Validar nombre duplicado solo si cambió
        if (request.getName() != null
                && !request.getName().equalsIgnoreCase(category.getName())
                && categoryRepository.existsByNameIgnoreCase(request.getName())) {
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

        // Validar que no se desactive si tiene subcategorías
        if (Boolean.FALSE.equals(request.getActive())
                && categoryRepository.existsByParentIdAndActiveTrue(id)) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("category.has.subcategories")
            );
        }

        // Validar que no se desactive si tiene productos activos
        if (Boolean.FALSE.equals(request.getActive())
                && productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION_ERROR,
                    getMessage("category.has.products")
            );
        }

        // Aplicar cambios solo si vienen en el request
        if (request.getName()        != null) category.setName(request.getName());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getParentId()    != null) category.setParentId(request.getParentId());
        if (request.getSortOrder()   != null) category.setSortOrder(request.getSortOrder());
        if (request.getActive()      != null) category.setActive(request.getActive());

        Category updated = categoryRepository.save(category);
        log.info("Categoría actualizada: {} por usuario: {}", id, userId);

        return toResponse(updated);
    }

    // ── Eliminar categoría (soft delete) ──────────────────────────
    @Transactional
    public void delete(UUID id, UUID userId) {

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
        category.setDeletedAt(OffsetDateTime.now());
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
}