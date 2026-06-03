package com.miroapp.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import java.util.UUID;

// =====================================================================
// StockMovement — historial INMUTABLE de movimientos de stock
// =====================================================================
// ¿QUÉ ES UN MOVIMIENTO DE STOCK?
//   Cada vez que el stock de un producto cambia — por cualquier
//   motivo — se crea un registro aquí. Es el libro contable
//   del inventario. Permite auditar cualquier cambio.
//
// REGLA DE ORO — INMUTABILIDAD:
//   Este registro NUNCA se modifica ni se elimina físicamente.
//   Un error → se crea un movimiento de sentido contrario.
//   Por eso NO tiene:
//     updated_at  → no se actualiza
//     deleted_at  → no se elimina
//     @PreUpdate  → no aplica
//
//   Ejemplo de corrección de error:
//     Se registró entrada de 10 unidades por error
//     → Se crea un OUT de 10 unidades con ADJUSTMENT_OUT
//     → El historial queda completo e íntegro
//
// AUDITORÍA COMPLETA:
//   stock_before + quantity = stock_after (para IN)
//   stock_before - quantity = stock_after (para OUT)
//   Siempre se puede reconstruir el historial completo
//
// reference_id → el documento que originó el movimiento:
//   SALE → ID de la venta en sales
//   PURCHASE → ID de la orden de compra (futuro)
//   NULL → ajuste manual sin documento
// =====================================================================
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // Sucursal donde ocurrió el movimiento
  // En transferencias: quien envía registra OUT
  //                    quien recibe registra IN (2 registros)
  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  // Misma lógica que stock:
  // Sin variantes → product_id tiene valor, variant_id NULL
  // Con variantes → variant_id tiene valor, product_id NULL
  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "variant_id")
  private UUID variantId;

  // IN → stock sube | OUT → stock baja
  // @Enumerated(STRING) → guarda "IN" o "OUT" en la BD
  // Coincide con CHECK (type IN ('IN','OUT'))
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 3)
  private MovementType type;

  // Razón específica del movimiento
  // @Enumerated(STRING) → guarda el nombre del enum en BD
  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 20)
  private MovementReason reason;

  // Cantidad del movimiento — SIEMPRE POSITIVO
  // El tipo (IN/OUT) define si suma o resta
  // DECIMAL(19,4) → soporta granel (2.5 kg, 1.75 litros)
  // CHECK (quantity > 0) en la BD
  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  // Stock ANTES del movimiento — para auditoría
  // IN:  stock_before + quantity = stock_after
  // OUT: stock_before - quantity = stock_after
  @Column(name = "stock_before", nullable = false, precision = 19, scale = 4)
  private BigDecimal stockBefore;

  // Stock DESPUÉS del movimiento
  // Debe coincidir con stock.quantity tras el movimiento
  @Column(name = "stock_after", nullable = false, precision = 19, scale = 4)
  private BigDecimal stockAfter;

  // ID del documento que originó el movimiento
  // SALE → UUID de la venta
  // PURCHASE → UUID de la orden de compra (futuro)
  // NULL → ajuste manual sin documento
  @Column(name = "reference_id")
  private UUID referenceId;

  // Notas obligatorias para ajustes manuales
  // "Conteo físico mensual — diferencia detectada"
  // "Merma por vencimiento"
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  // Quién registró el movimiento — SIEMPRE requerido
  // Convención MIRO: viene del SecurityContext
  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  // Timestamp de creación — SIEMPRE UTC
  // Es el único timestamp — no hay updated_at ni deleted_at
  @Column(name = "created_at",
    nullable = false,
    updatable = false)
  private OffsetDateTime createdAt;

  // ── @PrePersist ───────────────────────────────────────────────
  // Solo maneja createdAt — no hay @PreUpdate
  // Inmutabilidad garantizada por ausencia de updated_at
  @PrePersist
  protected void onCreate() {
    this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}
