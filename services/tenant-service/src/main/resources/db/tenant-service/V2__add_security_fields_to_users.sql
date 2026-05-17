-- =====================================================================
-- MIRO ERP — Migración de schema tenant
-- Versión: V2
-- Nombre: add_security_fields_to_users
-- Descripción: Agrega campos de seguridad a la tabla users:
--              must_change_password → obliga cambio de contraseña
--              failed_attempts      → contador de intentos fallidos
--              locked_until         → bloqueo temporal de cuenta
--              password_changed_at  → auditoría de cambio de contraseña
-- Fecha: 2026-05-15
--
-- IMPORTANTE: Este script es idempotente gracias a IF NOT EXISTS
-- Si los campos ya existen → no falla, no hace nada
-- Esto es importante porque empresa a ya tiene los campos
-- agregados manualmente en desarrollo
-- =====================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN
        NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS failed_attempts SMALLINT
        NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE;