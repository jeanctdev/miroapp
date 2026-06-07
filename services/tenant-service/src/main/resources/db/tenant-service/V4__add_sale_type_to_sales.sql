-- =====================================================================
-- MIRO ERP — MIGRACIÓN DE BASE DE DATOS
-- Schema: tenant (se aplica en el schema de cada empresa)
-- Archivo: V4__add_sale_type_to_sales.sql
-- Descripción: Agrega el tipo de venta a la tabla de ventas
-- Autor: Jean Paul Cochachin Torres
-- Contacto: jeanct.dev@gmail.com
-- Versión: V4
-- Sprint: 3
-- Fecha: 2026-06-06
-- =====================================================================
--
-- ¿POR QUÉ ESTA MIGRACIÓN?
-- La tabla sales fue creada en V1 con un modelo genérico de ventas.
-- En el análisis de negocio de Sprint 3 se identificaron dos tipos
-- de venta con flujos completamente distintos:
--
--   SIMPLE  → venta directa en el acto
--             Ejemplos: tienda física, venta por WhatsApp,
--             farmacia, veterinaria, cualquier POS
--             Flujo: crear venta → cobrar → cerrar
--
--   PROJECT → venta por etapas con entregables
--             Ejemplos: instalación de alarmas, construcción,
--             taller mecánico, catering, clínica de ortodoncia
--             Flujo: cotización → aprobación → etapas
--                  → cobros parciales → cierre
--
-- ¿POR QUÉ DEFAULT 'SIMPLE' Y NO NULL?
-- Porque sale_type es un campo requerido de negocio — toda venta
-- debe tener un tipo definido. NULL significaría "no sé qué tipo
-- de venta es", lo cual no tiene sentido en producción.
-- Además, toda la data existente en sales corresponde a ventas
-- simples (pruebas de POS), por lo que SIMPLE es el default correcto.
--
-- ¿POR QUÉ VARCHAR(10) Y NO UN ENUM DE PostgreSQL?
-- Los ENUM de PostgreSQL son difíciles de alterar — agregar un
-- valor nuevo requiere ALTER TYPE con bloqueos de tabla.
-- VARCHAR(10) con CHECK constraint es igual de seguro a nivel
-- de integridad pero más flexible para futuras extensiones.
-- 10 caracteres es suficiente para los valores actuales y futuros:
-- 'SIMPLE' = 6 chars, 'PROJECT' = 7 chars — sobra margen.
--
-- ¿POR QUÉ UN ÍNDICE EN sale_type?
-- Porque los reportes y filtros más comunes del sistema serán:
--   → "dame todas las ventas simples de hoy"
--   → "dame todos los proyectos activos de esta sucursal"
-- Sin índice, PostgreSQL haría full scan de toda la tabla sales.
-- Con índice, va directo a las filas del tipo solicitado.
-- El costo de crearlo es mínimo comparado con la ganancia
-- en consultas frecuentes.
--
-- IMPACTO EN OTROS SERVICIOS:
-- sales-service (Sprint 4) leerá y escribirá este campo.
-- project-service (Sprint 5) usará sale_type = 'PROJECT'
-- como punto de entrada para crear el proyecto asociado.
-- =====================================================================


-- =====================================================================
-- PASO 1: Agregar columna sale_type a la tabla sales
-- =====================================================================
--
-- IF NOT EXISTS → protege contra ejecuciones duplicadas.
-- Si por alguna razón Flyway ejecuta esta migración dos veces
-- (lo cual no debería pasar), el ALTER no falla — simplemente
-- no hace nada si la columna ya existe.
--
-- NOT NULL → toda venta debe tener tipo definido, nunca NULL
-- DEFAULT 'SIMPLE' → valor por defecto para:
--   a) registros existentes que no tenían este campo
--   b) inserts futuros que no especifiquen el tipo
-- CHECK → restricción de integridad a nivel de BD
--   PostgreSQL rechazará cualquier valor distinto a SIMPLE o PROJECT
--   Esto es la última línea de defensa — aunque el código Java
--   valide antes, la BD garantiza consistencia absoluta
--
-- =====================================================================

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS sale_type VARCHAR(10)
        NOT NULL DEFAULT 'SIMPLE'
        CHECK (sale_type IN ('SIMPLE', 'PROJECT'));


-- =====================================================================
-- PASO 2: Crear índice para búsquedas y filtros por tipo de venta
-- =====================================================================
--
-- idx_sales_sale_type → nombre siguiendo la convención del proyecto:
--   idx_{tabla}_{columna}
-- Todos los índices del schema siguen esta convención desde V1.
--
-- IF NOT EXISTS → mismo principio que el ALTER TABLE anterior.
-- En condiciones normales Flyway garantiza ejecución única,
-- pero la protección defensiva no tiene costo alguno.
--
-- Este índice será usado principalmente por:
--   → sales-service al filtrar ventas por tipo
--   → dashboard al calcular métricas separadas por tipo
--   → project-service al buscar ventas tipo PROJECT
--
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_sales_sale_type ON sales(sale_type);