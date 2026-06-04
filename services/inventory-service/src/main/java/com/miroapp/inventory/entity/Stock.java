package com.miroapp.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// =====================================================================
// Stock — cantidad actual de un producto por sucursal
// =====================================================================
// ¿QUÉ ES ESTE REGISTRO?
//   Un registro de stock representa la cantidad disponible
//   de UN producto (o variante) en UNA sucursal específica.
//
//   Ejemplo Venedog — Sucursal Miraflores:
//     Royal Canin 15kg → quantity: 8.0000
//     Amoxicilina 500mg → quantity: 15.0000
//     Collar Talla M  → quantity: 3.0000
//
// CONSTRAINT ÚNICO:
//   UNIQUE (branch_id, product_id, variant_id)
//   → Solo puede existir UN registro por producto/variante/sucursal
//   → Al ajustar stock → UPDATE del registro, no INSERT nuevo
//
// ¿QUIÉN TIENE REGISTRO DE STOCK?
//   PHYSICAL + track_stock=true  → SÍ tiene registro
//   PHYSICAL + track_stock=false → NO tiene registro
//   SERVICE                      → NUNCA tiene registro
//   DIGITAL                      → NUNCA tiene registro
//
// EXCLUSIVIDAD product_id / variant_id:
//   Producto sin variantes → product_id=UUID, variant_id=NULL
//   Producto con variantes → product_id=NULL, variant_id=UUID
//   NUNCA los dos con valor al mismo tiempo
//
// SOFT DELETE:
//   @SQLRestriction filtra deleted_at IS NULL
//   Un registro de stock inactivo no aparece en ninguna query
// =====================================================================
@Entity
@Table(name = "stock")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // Sucursal donde está físicamente el stock
  // Sin @ManyToOne — convención MIRO: relaciones con UUID directo
  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  // Producto sin variantes → tiene valor aquí
  // Producto con variantes → NULL aquí, usar variant_id
  @Column(name = "product_id")
  private UUID productId;

  // Variante específica del producto → tiene valor aquí
  // Producto sin variantes → NULL aquí, usar product_id
  @Column(name = "variant_id")
  private UUID variantId;

  // Cantidad actual disponible en la sucursal
  // DECIMAL(19,4) → soporta productos por peso y volumen
  // Ejemplos: 2.5 kg de queso, 1.75 litros de aceite
  // DEFAULT 0 → producto nuevo sin stock ingresado
  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  // Cantidad mínima antes de generar alerta
  // Cuando quantity <= min_quantity → el sistema notifica
  // 0 → sin alerta configurada para este producto
  // Cada sucursal puede tener su propio mínimo
  @Column(name = "min_quantity",
    nullable = false,
    precision = 19, scale = 4)
  private BigDecimal minQuantity;

  // Cantidad máxima recomendada — NULL = sin límite
  // Útil para evitar sobre-stock en sucursales pequeñas
  @Column(name = "max_quantity",
    precision = 19, scale = 4)
  private BigDecimal maxQuantity;

  // Timestamp de la última actualización de cantidad
  // Se actualiza cada vez que entra o sale stock
  // Diferente a updatedAt — es específico para stock
  @Column(name = "last_updated_at", nullable = false)
  private OffsetDateTime lastUpdatedAt;

  // Auditoría estándar
  @Column(name = "created_at",
    nullable = false,
    updatable = false)
  private OffsetDateTime createdAt;

  // UUID del usuario que creó el registro de stock
  // Primer ingreso de stock o configuración inicial
  @Column(name = "created_by")
  private UUID createdBy;

  // ── @PrePersist ───────────────────────────────────────────────
  // Se ejecuta antes del INSERT en BD
  // Convención MIRO: ZoneOffset.UTC en todos los timestamps
  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    this.createdAt     = now;
    this.lastUpdatedAt = now;
    if (this.quantity    == null)
      this.quantity    = BigDecimal.ZERO;
    if (this.minQuantity == null)
      this.minQuantity = BigDecimal.ZERO;
  }

}
