package com.miroapp.product.repository;

import com.miroapp.product.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// =====================================================================
// CategoryRepository — acceso a la tabla categories
// =====================================================================
// REGLA MIRO: todas las listas son paginadas con Page<T> + Pageable.
// El frontend siempre recibe datos paginados — nunca listas completas.
// @SQLRestriction en la entidad garantiza WHERE deleted_at IS NULL.
// =====================================================================
@Repository
public interface CategoryRepository
        extends JpaRepository<Category, UUID> {

    // Todas las categorías activas paginadas
    Page<Category> findAllByActiveTrue(Pageable pageable);

    // Categorías raíz activas — sin categoría padre
    Page<Category> findAllByParentIdIsNullAndActiveTrue(
            Pageable pageable);

    // Subcategorías de una categoría padre específica
    Page<Category> findAllByParentIdAndActiveTrue(
            UUID parentId, Pageable pageable);

    // Búsqueda por nombre — barra de búsqueda
    Page<Category> findAllByNameContainingIgnoreCaseAndActiveTrue(
            String name, Pageable pageable);

    // Verificar nombre duplicado antes de crear
    boolean existsByNameIgnoreCase(String name);

    // Buscar por nombre exacto — para validación
    Optional<Category> findByNameIgnoreCase(String name);

    // Verificar si tiene subcategorías activas
    // Antes de desactivar una categoría padre
    boolean existsByParentIdAndActiveTrue(UUID parentId);
}