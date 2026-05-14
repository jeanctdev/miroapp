-- =====================================================================
-- MIRO ERP — DISEÑO DE BASE DE DATOS
-- Schema: public
-- Descripción: Tablas compartidas entre todos los tenants del sistema
-- Autor: Jean Paul Cochachin Torres
-- Contacto: jeanct.dev@gmail.com
-- Versión: 1.0.0
-- Fecha: 2026-05-12
-- =====================================================================
--
-- ¿QUÉ ES EL SCHEMA PUBLIC?
-- Es el espacio compartido de la base de datos. Aquí viven los datos
-- que pertenecen al SISTEMA MIRO en sí, no a ninguna empresa cliente.
-- Cada empresa cliente (tenant) tiene su propio schema separado.
--
-- ESTRUCTURA:
-- public.countries             → catálogo de países soportados
-- public.currencies            → catálogo de monedas soportadas
-- public.tax_types             → tipos de impuesto por país
-- public.plans                 → planes de suscripción
-- public.tenants               → cada empresa cliente
-- public.tenant_subscriptions  → historial de pagos y planes
-- =====================================================================

-- =====================================================================
-- EXTENSIONES
-- Habilita UUID con gen_random_uuid()
-- Se ejecuta una sola vez por base de datos
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- =====================================================================
-- TABLA 1: public.countries
-- Catálogo de países soportados por Miro.
-- Determina moneda, timezone, formato de fecha e impuestos del tenant.
-- =====================================================================
CREATE TABLE public.countries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(2)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    currency_code VARCHAR(3)  NOT NULL,
    timezone      VARCHAR(50) NOT NULL,
    date_format   VARCHAR(20) NOT NULL DEFAULT 'DD/MM/YYYY',
    active        BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_countries_code ON public.countries(code);

INSERT INTO public.countries
    (id, code, name, currency_code, timezone, date_format, active, created_at)
VALUES
(gen_random_uuid(),'PE','Perú',          'PEN','America/Lima',                   'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'CO','Colombia',       'COP','America/Bogota',                 'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'MX','México',         'MXN','America/Mexico_City',            'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'VE','Venezuela',      'VES','America/Caracas',                'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'CL','Chile',          'CLP','America/Santiago',               'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'AR','Argentina',      'ARS','America/Argentina/Buenos_Aires', 'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'EC','Ecuador',        'USD','America/Guayaquil',              'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'BR','Brasil',         'BRL','America/Sao_Paulo',              'DD/MM/YYYY',true,NOW()),
(gen_random_uuid(),'US','Estados Unidos', 'USD','America/New_York',               'MM/DD/YYYY',true,NOW());


-- =====================================================================
-- TABLA 2: public.currencies
-- Catálogo de monedas. Separada de countries porque una moneda
-- puede usarse en múltiples países (USD en Ecuador, El Salvador, etc).
-- =====================================================================
CREATE TABLE public.currencies (
    code           VARCHAR(3)  PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    symbol         VARCHAR(10) NOT NULL,
    symbol_native  VARCHAR(10) NOT NULL,
    decimal_digits SMALLINT    NOT NULL DEFAULT 2,
    active         BOOLEAN     NOT NULL DEFAULT true,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO public.currencies
    (code, name, symbol, symbol_native, decimal_digits, active, created_at)
VALUES
('PEN','Sol Peruano',          'S/', 'PEN',2,true,NOW()),
('USD','Dólar Estadounidense', '$',  'USD',2,true,NOW()),
('COP','Peso Colombiano',      '$',  'COP',2,true,NOW()),
('MXN','Peso Mexicano',        '$',  'MXN',2,true,NOW()),
('VES','Bolívar Venezolano',   'Bs.','VES',2,true,NOW()),
('CLP','Peso Chileno',         '$',  'CLP',0,true,NOW()),
('ARS','Peso Argentino',       '$',  'ARS',2,true,NOW()),
('BRL','Real Brasileño',       'R$', 'BRL',2,true,NOW()),
('EUR','Euro',                 '€',  'EUR',2,true,NOW());


-- =====================================================================
-- TABLA 3: public.tax_types
-- Impuestos por país. Hace que Miro funcione en toda LATAM
-- sin hardcodear nada en el código Java.
-- =====================================================================
CREATE TABLE public.tax_types (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code  VARCHAR(2)  NOT NULL REFERENCES public.countries(code),
    name          VARCHAR(100) NOT NULL,
    code          VARCHAR(10) NOT NULL,
    rate          DECIMAL(5,2) NOT NULL,
    is_inclusive  BOOLEAN     NOT NULL DEFAULT true,
    is_default    BOOLEAN     NOT NULL DEFAULT false,
    active        BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_types_country ON public.tax_types(country_code);

INSERT INTO public.tax_types
    (id, country_code, name, code, rate, is_inclusive, is_default, active, created_at)
VALUES
(gen_random_uuid(),'PE','Impuesto General a las Ventas','IGV', 18.00,true, true, true,NOW()),
(gen_random_uuid(),'PE','Exonerado',                    'EXO',  0.00,true, false,true,NOW()),
(gen_random_uuid(),'CO','Impuesto al Valor Agregado',   'IVA', 19.00,true, true, true,NOW()),
(gen_random_uuid(),'CO','Excluido',                     'EXC',  0.00,true, false,true,NOW()),
(gen_random_uuid(),'MX','Impuesto al Valor Agregado',   'IVA', 16.00,false,true, true,NOW()),
(gen_random_uuid(),'MX','Tasa Cero',                    'TZ',   0.00,false,false,true,NOW()),
(gen_random_uuid(),'VE','Impuesto al Valor Agregado',   'IVA', 16.00,true, true, true,NOW()),
(gen_random_uuid(),'CL','Impuesto al Valor Agregado',   'IVA', 19.00,true, true, true,NOW()),
(gen_random_uuid(),'AR','Impuesto al Valor Agregado',   'IVA', 21.00,false,true, true,NOW()),
(gen_random_uuid(),'EC','Impuesto al Valor Agregado',   'IVA', 12.00,true, true, true,NOW()),
(gen_random_uuid(),'BR','Imposto sobre Circulação',     'ICMS',17.00,false,true, true,NOW()),
(gen_random_uuid(),'US','Sales Tax',                    'ST',   8.00,false,true, true,NOW());


-- =====================================================================
-- TABLA 4: public.plans
-- Planes de suscripción de Miro.
-- Starter $29 · Business $59 · Enterprise $99
-- =====================================================================
CREATE TABLE public.plans (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(50)  NOT NULL UNIQUE,
    description   TEXT,
    monthly_price DECIMAL(10,2) NOT NULL,
    annual_price  DECIMAL(10,2) NOT NULL,
    max_branches  SMALLINT     NOT NULL DEFAULT 1,
    max_users     SMALLINT     NOT NULL DEFAULT 5,
    max_products  INTEGER      NOT NULL DEFAULT 500,
    features      JSONB        NOT NULL DEFAULT '{}',
    is_featured   BOOLEAN      NOT NULL DEFAULT false,
    display_order SMALLINT     NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO public.plans
    (id, name, description, monthly_price, annual_price,
     max_branches, max_users, max_products,
     features, is_featured, display_order, active, created_at, updated_at)
VALUES
(
    gen_random_uuid(),
    'Starter',
    'Perfecto para negocios pequeños que quieren empezar a organizarse',
    29.00, 290.00, 1, 5, 500,
    '{"pos":true,"inventory":true,"finance":true,"hr":false,"crm":false,"ai":false,"api_access":false}',
    false, 1, true, NOW(), NOW()
),
(
    gen_random_uuid(),
    'Business',
    'Para negocios en crecimiento con múltiples sucursales y más control',
    59.00, 590.00, 3, 15, 5000,
    '{"pos":true,"inventory":true,"finance":true,"hr":true,"crm":true,"ai":true,"api_access":false}',
    true, 2, true, NOW(), NOW()
),
(
    gen_random_uuid(),
    'Enterprise',
    'Para empresas establecidas que necesitan control total sin límites',
    99.00, 990.00, 999, 999, 999999,
    '{"pos":true,"inventory":true,"finance":true,"hr":true,"crm":true,"ai":true,"api_access":true}',
    false, 3, true, NOW(), NOW()
);


-- =====================================================================
-- TABLA 5: public.tenants
-- La tabla más importante del schema public.
-- Cada fila = una empresa cliente de Miro.
-- Al registrarse → tenant-service crea su schema automáticamente.
-- Soft delete: nunca se borran con DELETE, se usa deleted_at.
-- =====================================================================
CREATE TABLE public.tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          VARCHAR(63)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    country_code  VARCHAR(2)   NOT NULL REFERENCES public.countries(code),
    currency_code VARCHAR(3)   NOT NULL REFERENCES public.currencies(code),
    plan_id       UUID         NOT NULL REFERENCES public.plans(id),
    admin_email   VARCHAR(255) NOT NULL UNIQUE,
    admin_name    VARCHAR(200) NOT NULL,
    admin_phone   VARCHAR(20),
    tax_info      JSONB        NOT NULL DEFAULT '{}',
    status        VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'
                  CHECK (status IN ('TRIAL','ACTIVE','SUSPENDED','CANCELLED')),
    trial_ends_at TIMESTAMP WITH TIME ZONE,
    settings      JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_tenants_slug        ON public.tenants(slug);
CREATE INDEX idx_tenants_admin_email ON public.tenants(admin_email);
CREATE INDEX idx_tenants_status      ON public.tenants(status);
CREATE INDEX idx_tenants_deleted_at  ON public.tenants(deleted_at);


-- =====================================================================
-- TABLA 6: public.tenant_subscriptions
-- Historial completo de planes y pagos por tenant.
-- Cada vez que un tenant paga se crea una fila aquí.
-- =====================================================================
CREATE TABLE public.tenant_subscriptions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES public.tenants(id),
    plan_id        UUID          NOT NULL REFERENCES public.plans(id),
    billing_cycle  VARCHAR(10)   NOT NULL
                   CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
    amount_paid    DECIMAL(10,2) NOT NULL,
    currency_code  VARCHAR(3)    NOT NULL REFERENCES public.currencies(code),
    starts_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                   CHECK (payment_status IN ('PENDING','PAID','FAILED','REFUNDED')),
    payment_ref    VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_subs_tenant_id ON public.tenant_subscriptions(tenant_id);
CREATE INDEX idx_tenant_subs_status    ON public.tenant_subscriptions(payment_status);
CREATE INDEX idx_tenant_subs_ends_at   ON public.tenant_subscriptions(ends_at);