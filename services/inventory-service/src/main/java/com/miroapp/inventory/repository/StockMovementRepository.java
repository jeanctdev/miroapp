package com.miroapp.inventory.repository;

import com.miroapp.inventory.entity.MovementReason;
import com.miroapp.inventory.entity.MovementType;
import com.miroapp.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// =====================================================================
// StockMovementRepository — acceso a la tabla stock_movements
// =====================================================================
// stock_movements es INMUTABLE — sin @SQLRestriction de soft delete.
// La entidad NO tiene deleted_at ni updated_at.
// Todos los registros siempre son visibles.
//
// IMPORTANTE: Solo se hacen INSERT — NUNCA UPDATE ni DELETE.
// Si necesitas "corregir" un movimiento → crea uno nuevo
// de sentido contrario con las notas de la corrección.
// =====================================================================
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  // Historial de movimientos de una sucursal paginado
  // Usado en el backoffice para auditoría del inventario
  Page<StockMovement> findAllByBranchIdOrderByCreatedAtDesc(
    UUID branchId, Pageable pageable);

  // Historial de movimientos de un producto específico
  // Permite ver toda la actividad de un producto en el tiempo
  Page<StockMovement> findAllByProductIdOrderByCreatedAtDesc(
    UUID productId, Pageable pageable);

  // Historial de movimientos de una variante específica
  Page<StockMovement> findAllByVariantIdOrderByCreatedAtDesc(
    UUID variantId, Pageable pageable);

  // Filtrar por tipo — ver solo entradas o solo salidas
  // Útil para reportes: "¿cuánto entró este mes?"
  Page<StockMovement> findAllByBranchIdAndTypeOrderByCreatedAtDesc(
    UUID branchId,
    MovementType type,
    Pageable pageable);

  // Filtrar por razón — ver solo ventas, compras, etc.
  // Útil para análisis: "¿cuánto se vendió de este producto?"
  Page<StockMovement> findAllByBranchIdAndReasonOrderByCreatedAtDesc(
    UUID branchId,
    MovementReason reason,
    Pageable pageable);

  // Movimientos de una venta específica
  // Usado cuando se anula una venta — para ver qué stock
  // debe revertirse (crear movimientos contrarios)
  Page<StockMovement> findAllByReferenceId(
    UUID referenceId, Pageable pageable);

}
