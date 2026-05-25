package com.miroapp.product.repository;

import com.miroapp.product.entity.Product;
import com.miroapp.product.entity.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// =====================================================================
// ProductRepository — acceso a la tabla products
// =====================================================================
// REGLA MIRO: todas las listas son paginadas con Page<T> + Pageable.
// @SQLRestriction en la entidad garantiza WHERE deleted_at IS NULL.
// =====================================================================
@Repository
public interface ProductRepository
        extends JpaRepository<Product, UUID> {

    // Listado paginado de productos activos — backoffice
    Page<Product> findAllByActiveTrue(Pageable pageable);

    // Filtrar por categoría — POS y backoffice
    Page<Product> findAllByCategoryIdAndActiveTrue(
            UUID categoryId, Pageable pageable);

    // Filtrar por tipo — PHYSICAL, SERVICE, DIGITAL
    Page<Product> findAllByTypeAndActiveTrue(
            ProductType type, Pageable pageable);

    // Búsqueda por nombre — barra de búsqueda del POS
    Page<Product> findAllByNameContainingIgnoreCaseAndActiveTrue(
            String name, Pageable pageable);

    // Búsqueda por nombre dentro de una categoría
    Page<Product> findAllByNameContainingIgnoreCaseAndCategoryIdAndActiveTrue(
            String name, UUID categoryId, Pageable pageable);

    // Buscar por SKU — validación y búsqueda directa en POS
    Optional<Product> findBySkuAndActiveTrue(String sku);

    // Buscar por código de barras — scanner del POS
    Optional<Product> findByBarcodeAndActiveTrue(String barcode);

    // Verificar SKU duplicado antes de crear o actualizar
    boolean existsBySku(String sku);

    // Verificar barcode duplicado antes de crear o actualizar
    boolean existsByBarcode(String barcode);

    // Verificar si categoría tiene productos activos
    // Antes de desactivar una categoría
    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);
}