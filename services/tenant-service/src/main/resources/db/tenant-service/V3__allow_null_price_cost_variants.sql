-- =====================================================================
-- MIRO ERP — Migración V3
-- Nombre: allow_null_price_cost_variants
-- Descripción: Permite null en price y cost de product_variants
--              para implementar herencia de precio del producto padre.
--
-- REGLA DE NEGOCIO:
--   price null → la variante hereda products.base_price del padre
--   cost null  → la variante hereda products.base_cost del padre
--   price 0    → la variante tiene precio cero (producto gratis)
--
-- Sin esta migración: price NOT NULL DEFAULT 0
--   No podemos distinguir entre "precio cero" y "hereda del padre"
--   Con null: semántica clara y sin ambigüedad
-- =====================================================================

ALTER TABLE product_variants
    ALTER COLUMN price DROP NOT NULL,
    ALTER COLUMN price DROP DEFAULT;

ALTER TABLE product_variants
    ALTER COLUMN cost DROP NOT NULL,
    ALTER COLUMN cost DROP DEFAULT;