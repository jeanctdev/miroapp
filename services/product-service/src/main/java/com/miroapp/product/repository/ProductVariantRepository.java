package com.miroapp.product.repository;

import com.miroapp.product.entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// =====================================================================
// ProductVariantRepository — acceso a datos de variantes
// =====================================================================
// ¿QUÉ ES UN REPOSITORY EN SPRING DATA JPA?
//   Es una interfaz que Spring implementa automáticamente.
//   No escribimos SQL ni implementamos métodos — Spring los genera
//   en tiempo de compilación leyendo el nombre del método.
//
//   Ejemplo: findAllByProductIdAndActiveTrue(productId, pageable)
//   Spring lee: "find All By ProductId And Active True"
//   Genera:     SELECT * FROM product_variants
//               WHERE product_id = ? AND active = true
//               ORDER BY sort_order ASC  ← lo define Pageable
//
// ¿POR QUÉ TODOS LOS LISTADOS USAN Page<T> Y NO List<T>?
//   Un producto puede tener muchas variantes (decenas de tallas,
//   colores, sabores). Sin paginación → traemos todo → memoria
//   se agota. Con paginación → traemos lo que se muestra.
//
// ¿POR QUÉ NO ESCRIBIMOS @Query?
//   Spring Data JPA puede generar las queries simples
//   leyendo el nombre del método — sin SQL manual.
//   Solo usamos @Query para queries muy complejas.
//   Mantiene el código limpio y sin SQL hardcodeado.
//
// @SQLRestriction("deleted_at IS NULL") en la entidad:
//   Filtra automáticamente los eliminados (soft delete)
//   en TODAS las queries de este repository — sin where manual.
// =====================================================================
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

  // ── Listar variantes activas de un producto ───────────────────
  // Usado cuando el cajero selecciona un producto con variantes:
  //   Cajero toca "Collar Nylon" → aparecen: S, M, L, XL
  // Ordenadas por sort_order → el dueño decide el orden visual
  Page<ProductVariant> findAllByProductIdAndActiveTrue(
    UUID productId, Pageable pageable);

  // ── Verificar SKU duplicado ───────────────────────────────────
  // Validamos ANTES de intentar insertar para dar mensaje claro.
  // Si dejamos que falle el UNIQUE constraint de la BD →
  // el usuario ve un error técnico incomprensible.
  // Con esta validación → ve "Ya existe un SKU ACC-COL-NYL-M"
  boolean existsBySku(String sku);

  // ── Verificar barcode duplicado ───────────────────────────────
  // Igual que SKU — validamos antes del constraint de BD.
  boolean existsByBarcode(String barcode);

  // ── Buscar por SKU — escritura manual en el POS ───────────────
  // El cajero escribe el SKU directamente en el teclado del POS.
  // Devuelve Optional porque puede no existir ese SKU.
  // andActiveTrue → no devuelve variantes eliminadas/inactivas.
  Optional<ProductVariant> findBySkuAndActiveTrue(String sku);

  // ── Buscar por barcode — scanner del POS ──────────────────────
  // El cajero pasa el producto por el lector de código de barras.
  // El scanner envía el barcode → sistema devuelve la variante.
  // Es la forma más rápida de agregar un producto en el POS.
  Optional<ProductVariant> findByBarcodeAndActiveTrue(String barcode);

  // ── Contar variantes activas de un producto ───────────────────
  // Usado ANTES de hacer soft delete del producto padre.
  // Regla de negocio: no se puede eliminar un producto padre
  // si tiene variantes activas → primero eliminar las variantes.
  // count en vez de existsBy → permite dar mensaje con el número:
  //   "No puedes eliminar este producto — tiene 4 variantes activas"
  long countByProductIdAndActiveTrue(UUID productId);
}
