package com.miroapp.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// Product — entidad que mapea la tabla products del schema tenant
// =====================================================================
// La tabla products ya existe — creada por tenant-service via V1.
// JPA solo la mapea, no la crea ni modifica.
//
// Tres tipos de producto:
//   PHYSICAL → tiene stock, usa product_variants, barcode para scanner
//   SERVICE  → mano de obra, no tiene stock, no se puede escanear
//   DIGITAL  → archivo o licencia, no tiene stock
//
// has_variants → true significa que el precio real está en
//   product_variants, no en base_price de este producto.
//   Ejemplo: collar para perro tiene variantes S, M, L con
//   precios distintos.
//
// tax_type_id → apunta a public.tax_types (IGV 18%, IVA 19%, etc.)
//   El schema del tenant referencia al schema public.
// =====================================================================
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Categoría a la que pertenece — nullable
    // NULL → producto sin categoría asignada
    @Column(name = "category_id")
    private UUID categoryId;

    // Nombre del producto
    // Ejemplos: "Amoxicilina 500mg", "Collar para perro", "Consulta médica"
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // Descripción detallada — opcional
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Tipo de producto — determina comportamiento en el sistema
    // PHYSICAL → descuenta stock al vender
    // SERVICE  → no descuenta stock (mano de obra, tiempo)
    // DIGITAL  → no descuenta stock (archivos, licencias)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ProductType type;

    // SKU interno del negocio — único pero opcional
    // Ejemplos: MED-001, VET-CORTE, CONS-001
    @Column(name = "sku", length = 100)
    private String sku;

    // Código de barras para scanner en el POS — solo PHYSICAL
    @Column(name = "barcode", length = 100)
    private String barcode;

    // Precio de venta al público con 4 decimales de precisión
    // Si has_variants=true → el precio real está en product_variants
    @Column(name = "base_price", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal basePrice;

    // Costo de adquisición — para calcular margen de ganancia
    @Column(name = "base_cost", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal baseCost;

    // Referencia al tipo de impuesto en public.tax_types
    // IGV 18% (PE), IVA 19% (CO), IVA 16% (MX), etc.
    // NULL → sin impuesto asignado
    @Column(name = "tax_type_id")
    private UUID taxTypeId;

    // Unidad de medida
    // Ejemplos: unidad, kg, litro, metro, hora, par, docena
    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    // true → tiene variantes (talla, color, sabor)
    //        habilita el módulo de product_variants
    // false → producto simple sin variantes
    @Column(name = "has_variants", nullable = false)
    private Boolean hasVariants;

    // true  → PHYSICAL: genera movimientos de stock al vender
    // false → SERVICE y DIGITAL: nunca generan movimientos
    @Column(name = "track_stock", nullable = false)
    private Boolean trackStock;

    // URL de la imagen principal en Azure Blob Storage
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // false → oculto en el POS pero conserva historial de ventas
    @Column(name = "active", nullable = false)
    private Boolean active;

    // ── Auditoría ─────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // UUID del usuario que creó el producto
    // Viene del header X-User-Id propagado por el gateway
    @Column(name = "created_by")
    private UUID createdBy;

    // NULL → activo | fecha → eliminado (soft delete)
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // ── Hooks de JPA ──────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt   = now;
        this.updatedAt   = now;
        if (this.active      == null) this.active      = true;
        if (this.hasVariants == null) this.hasVariants = false;
        if (this.trackStock  == null) this.trackStock  = true;
        if (this.basePrice   == null) this.basePrice   = BigDecimal.ZERO;
        if (this.baseCost    == null) this.baseCost    = BigDecimal.ZERO;
        if (this.unit        == null) this.unit        = "unidad";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}