package com.miroapp.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// =====================================================================
// ProductVariant — variante de un producto padre
// =====================================================================
// ¿QUÉ ES UNA VARIANTE?
//   Una versión específica de un producto que difiere
//   en atributos como talla, color, sabor, peso, etc.
//
//   Ejemplo real Venedog:
//     Producto padre: "Collar Nylon Ajustable" (hasVariants=true)
//       Variante 1: Talla S → sku: ACC-COL-NYL-S, precio: S/20.00
//       Variante 2: Talla M → sku: ACC-COL-NYL-M, precio: S/25.00
//       Variante 3: Talla L → sku: ACC-COL-NYL-L, precio: S/30.00
//
// ¿CUÁNDO APLICA?
//   Solo a productos PHYSICAL con hasVariants=true.
//   SERVICE y DIGITAL no tienen variantes — no tiene sentido
//   tener "tallas" de una consulta médica.
//
// HERENCIA DEL PADRE:
//   price null    → el service usa products.base_price
//   cost null     → el service usa products.base_cost
//   type          → siempre hereda (siempre PHYSICAL)
//   unit          → hereda del padre
//   tax_type_id   → hereda del padre
//   track_stock   → hereda del padre
//
// NOMENCLATURA IMPORTANTE:
//   La BD usa "price" y "cost" (no "base_price"/"base_cost")
//   Diferente a la tabla products — respetar la BD exactamente
//
// ATTRIBUTES JSONB:
//   Flexible — cada negocio define sus propios atributos
//   {"talla": "M"}
//   {"peso": "15kg", "sabor": "pollo"}
//   {"color": "rojo", "talla": "XL"}
//   El frontend muestra estos atributos como chips/tags
//
// SOFT DELETE:
//   @SQLRestriction filtra deleted_at IS NULL automáticamente
//   NUNCA DELETE físico — los datos son inmutables
// =====================================================================
@Entity
@Table(name = "product_variants")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // UUID del producto padre
  // Convención MIRO: sin @ManyToOne — sin relaciones JPA
  // El service resuelve el producto padre cuando lo necesita
  // Esto evita lazy loading inesperado y N+1 queries
  @Column(name = "product_id", nullable = false)
  private UUID productId;

  // Nombre de la variante — el atributo diferenciador
  // Ejemplos: "Talla S", "3kg", "Sabor Pollo y Arroz"
  // El cajero ve este nombre en el POS al seleccionar variante
  @Column(nullable = false, length = 200)
  private String name;

  // SKU único por tenant
  // Normalmente el SKU del padre + sufijo de la variante
  // Ejemplo: ACC-COL-NYL-S, ACC-COL-NYL-M, ACC-COL-NYL-L
  // UNIQUE en BD — validamos ANTES en el service
  // para dar mensaje claro al usuario
  @Column(length = 100)
  private String sku;

  // Código de barras del empaque específico de esta variante
  // Cada talla/tamaño tiene su propio código de barras impreso
  // El scanner del POS lo lee y llega directo a esta variante
  // UNIQUE en BD — validamos ANTES en el service
  @Column(length = 100)
  private String barcode;

  // Precio propio de esta variante
  // NULL → el service usa products.base_price del padre
  // Con valor → este precio prevalece sobre el del padre
  //
  // Ejemplo con precio diferente por variante:
  //   Royal Canin 3kg  → price: 85.00
  //   Royal Canin 8kg  → price: 195.00
  //   Royal Canin 15kg → price: 285.00
  //
  // Ejemplo con precio igual para todas las variantes:
  //   Collar Talla S → price: null (usa padre 25.00)
  //   Collar Talla M → price: null (usa padre 25.00)
  //   Collar Talla L → price: null (usa padre 25.00)
  //
  // DECIMAL(19,4) para precisión en cálculos de IGV
  @Column(name = "price", precision = 19, scale = 4)
  private BigDecimal price;

  // Costo propio de esta variante
  // NULL → el service usa products.base_cost del padre
  // Con valor → este costo prevalece sobre el del padre
  @Column(name = "cost", precision = 19, scale = 4)
  private BigDecimal cost;

  // Atributos específicos de la variante en JSONB
  // JSONB en PostgreSQL → más rápido que JSON
  //   almacena en formato binario parseado
  //   soporta índices GIN para búsquedas dentro del JSON
  //
  // En Java lo manejamos como String porque:
  //   No necesitamos tipado estricto — es libre por diseño
  //   El frontend lo parsea y muestra como chips/tags
  //   Evita dependencias extra de Hibernate para JSONB
  //
  // Ejemplos reales:
  //   {"talla": "S"}
  //   {"talla": "M", "color": "rojo"}
  //   {"peso": "15kg", "sabor": "pollo y arroz"}
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String attributes;

  // URL de imagen específica de esta variante
  // NULL → el frontend usa la imagen del producto padre
  // Útil cuando las variantes tienen diferente apariencia
  // Ejemplo: collar rojo vs collar azul → imágenes distintas
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  // Orden de aparición en el POS cuando el cajero
  // ve las variantes disponibles de un producto
  // 0 → aparece primero, 1 → segundo, etc.
  @Column(name = "sort_order")
  private Short sortOrder;

  // true  → visible en el POS y el sistema
  // false → oculta — cuando una talla se descontinúa
  //         pero tiene historial de ventas que conservar
  @Column(nullable = false)
  private Boolean active;

  // Timestamp de creación — siempre en UTC
  // ZoneOffset.UTC → convención MIRO
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  // Timestamp de última actualización — siempre en UTC
  // El service lo actualiza manualmente en update()
  // No usamos @PreUpdate — convención MIRO
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // UUID del usuario que creó esta variante
  // Viene del SecurityContext — propagado por el gateway
  // Auditoría: quién agregó esta variante al catálogo
  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  // Soft delete — NUNCA DELETE físico
  // NULL    → variante activa
  // Fecha   → fue eliminada en esa fecha y hora
  // @SQLRestriction filtra automáticamente en todas las queries
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  // ── @PrePersist ───────────────────────────────────────────────
  // Se ejecuta automáticamente ANTES del INSERT en BD
  // Solo maneja la CREACIÓN del registro
  // El service controla UPDATE y DELETE manualmente
  // ZoneOffset.UTC → todos los timestamps en UTC
  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    this.createdAt  = now;
    this.updatedAt  = now;
    if (this.active     == null) this.active     = true;
    if (this.sortOrder  == null) this.sortOrder  = 0;
    if (this.attributes == null) this.attributes = "{}";
  }
}
