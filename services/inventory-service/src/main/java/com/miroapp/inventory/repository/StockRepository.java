package com.miroapp.inventory.repository;

import com.miroapp.inventory.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
// =====================================================================
// StockRepository — acceso a la tabla stock
// =====================================================================
// @SQLRestriction("deleted_at IS NULL") en la entidad:
//   Filtra automáticamente los registros inactivos.
//
// CONVENCIÓN MIRO:
//   Todos los listados paginados con Page<T> + Pageable.
//   Nunca List<T> en endpoints — podría traer miles de registros.
//
// Métodos derivados de nombre:
//   Spring Data JPA genera el SQL leyendo el nombre.
//   Sin @Query → sin SQL manual → más limpio y mantenible.
// =====================================================================
@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

  // Buscar stock por sucursal + producto sin variante
  // Usado al vender un producto simple en el POS
  Optional<Stock> findByBranchIdAndProductIdAndVariantIdIsNull(
    UUID branchId, UUID productId);

  // Buscar stock por sucursal + variante específica
  // Usado al vender una variante (Collar Talla M) en el POS
  Optional<Stock> findByBranchIdAndVariantId(
    UUID branchId, UUID variantId);

  // Listar todo el stock de una sucursal paginado
  // Vista principal del inventario en el backoffice
  Page<Stock> findAllByBranchId(
    UUID branchId, Pageable pageable);

  // Listar stock de un producto en todas las sucursales
  // Ver en qué sucursales hay stock de un producto
  Page<Stock> findAllByProductId(
    UUID productId, Pageable pageable);

  // Listar stock de una variante en todas las sucursales
  Page<Stock> findAllByVariantId(
    UUID variantId, Pageable pageable);

  // Listar productos con stock bajo el mínimo configurado
  // Para alertas y reposición — stock crítico
  // quantity <= min_quantity Y min_quantity > 0
  // (min_quantity = 0 significa sin alerta configurada)
  Page<Stock> findAllByBranchIdAndQuantityLessThanEqualAndMinQuantityGreaterThan(
    UUID branchId,
    BigDecimal quantity,
    BigDecimal minQuantity,
    Pageable pageable);

  // Verificar si existe un registro de stock
  // Antes de crear uno nuevo (para evitar duplicados)
  boolean existsByBranchIdAndProductIdAndVariantIdIsNull(
    UUID branchId, UUID productId);

  boolean existsByBranchIdAndVariantId(
    UUID branchId, UUID variantId);


}
