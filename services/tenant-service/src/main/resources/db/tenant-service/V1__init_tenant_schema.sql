-- =====================================================================
-- MIRO ERP — Migración de schema tenant
-- Versión: V1
-- Nombre: init_tenant_schema
-- Descripción: Crea las 13 tablas del schema de cada empresa cliente
--              Este script se ejecuta automáticamente cuando
--              se registra un nuevo tenant en el sistema.
--              El schema ya fue creado antes de ejecutar este script.
-- Fecha: 2026-05-15
-- =====================================================================

-- =====================================================================
-- TABLA 1: branches — Sucursales
-- =====================================================================
CREATE TABLE IF NOT EXISTS branches (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255),
    city       VARCHAR(100),
    phone      VARCHAR(20),
    email      VARCHAR(255),
    is_main    BOOLEAN  NOT NULL DEFAULT false,
    active     BOOLEAN  NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_branches_active ON branches(active);


-- =====================================================================
-- TABLA 2: users — Usuarios del sistema
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    phone                VARCHAR(20),
    role                 VARCHAR(20)  NOT NULL DEFAULT 'CASHIER'
                         CHECK (role IN ('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')),
    branch_id            UUID REFERENCES branches(id),
    avatar_url           VARCHAR(500),
    active               BOOLEAN NOT NULL DEFAULT true,
    last_login_at        TIMESTAMP WITH TIME ZONE,

    -- ─── SEGURIDAD ──────────────────────────────────────────────
    -- true  → debe cambiar su contraseña al próximo login
    -- false → ya cambió su contraseña
    must_change_password BOOLEAN   NOT NULL DEFAULT false,

    -- Contador de intentos fallidos de login
    -- Al llegar a 5 → cuenta bloqueada
    -- Se resetea a 0 al hacer login exitoso
    failed_attempts      SMALLINT  NOT NULL DEFAULT 0,

    -- Hasta cuándo está bloqueada la cuenta
    -- NULL    → cuenta activa
    -- datetime → bloqueada hasta esa fecha/hora
    locked_until         TIMESTAMP WITH TIME ZONE,

    -- Cuándo cambió su contraseña por última vez
    -- Útil para políticas de vencimiento en el futuro
    password_changed_at  TIMESTAMP WITH TIME ZONE,

    -- ─── AUDITORÍA ──────────────────────────────────────────────
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by           UUID,
    deleted_at           TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_users_email   ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_branch  ON users(branch_id);
CREATE INDEX IF NOT EXISTS idx_users_role    ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active  ON users(active);


-- =====================================================================
-- TABLA 3: categories — Categorías de productos
-- =====================================================================
CREATE TABLE IF NOT EXISTS categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id   UUID REFERENCES categories(id),
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    active      BOOLEAN  NOT NULL DEFAULT true,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by  UUID,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_categories_parent ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_active ON categories(active);


-- =====================================================================
-- TABLA 4: products — Catálogo de productos
-- =====================================================================
CREATE TABLE IF NOT EXISTS products (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id  UUID REFERENCES categories(id),
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    type         VARCHAR(20)  NOT NULL DEFAULT 'PHYSICAL'
                 CHECK (type IN ('PHYSICAL','SERVICE','DIGITAL')),
    sku          VARCHAR(100) UNIQUE,
    barcode      VARCHAR(100) UNIQUE,
    base_price   DECIMAL(19,4) NOT NULL DEFAULT 0,
    base_cost    DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_type_id  UUID REFERENCES public.tax_types(id),
    unit         VARCHAR(50)  NOT NULL DEFAULT 'unidad',
    has_variants BOOLEAN      NOT NULL DEFAULT false,
    track_stock  BOOLEAN      NOT NULL DEFAULT true,
    image_url    VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by   UUID,
    deleted_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_products_name     ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_type     ON products(type);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_barcode  ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_sku      ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_active   ON products(active);


-- =====================================================================
-- TABLA 5: product_variants — Variantes de productos
-- =====================================================================
CREATE TABLE IF NOT EXISTS product_variants (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    name       VARCHAR(200) NOT NULL,
    sku        VARCHAR(100) UNIQUE,
    barcode    VARCHAR(100) UNIQUE,
    price      DECIMAL(19,4) NOT NULL DEFAULT 0,
    cost       DECIMAL(19,4) NOT NULL DEFAULT 0,
    attributes JSONB        NOT NULL DEFAULT '{}',
    image_url  VARCHAR(500),
    sort_order SMALLINT     NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_variants_product ON product_variants(product_id);
CREATE INDEX IF NOT EXISTS idx_variants_barcode ON product_variants(barcode);
CREATE INDEX IF NOT EXISTS idx_variants_sku     ON product_variants(sku);
CREATE INDEX IF NOT EXISTS idx_variants_active  ON product_variants(active);


-- =====================================================================
-- TABLA 6: stock — Stock actual
-- =====================================================================
CREATE TABLE IF NOT EXISTS stock (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       UUID NOT NULL REFERENCES branches(id),
    product_id      UUID REFERENCES products(id),
    variant_id      UUID REFERENCES product_variants(id),
    quantity        DECIMAL(19,4) NOT NULL DEFAULT 0,
    min_quantity    DECIMAL(19,4) NOT NULL DEFAULT 0,
    max_quantity    DECIMAL(19,4),
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID,
    CONSTRAINT uq_stock_branch_product
        UNIQUE (branch_id, product_id, variant_id)
);

CREATE INDEX IF NOT EXISTS idx_stock_branch   ON stock(branch_id);
CREATE INDEX IF NOT EXISTS idx_stock_product  ON stock(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_variant  ON stock(variant_id);
CREATE INDEX IF NOT EXISTS idx_stock_quantity ON stock(quantity);


-- =====================================================================
-- TABLA 7: stock_movements — Historial inmutable
-- =====================================================================
CREATE TABLE IF NOT EXISTS stock_movements (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id    UUID NOT NULL REFERENCES branches(id),
    product_id   UUID REFERENCES products(id),
    variant_id   UUID REFERENCES product_variants(id),
    type         VARCHAR(3)   NOT NULL CHECK (type IN ('IN','OUT')),
    reason       VARCHAR(20)  NOT NULL
                 CHECK (reason IN (
                     'PURCHASE','SALE',
                     'TRANSFER_IN','TRANSFER_OUT',
                     'ADJUSTMENT_IN','ADJUSTMENT_OUT',
                     'RETURN_CUSTOMER','RETURN_SUPPLIER'
                 )),
    quantity     DECIMAL(19,4) NOT NULL CHECK (quantity > 0),
    stock_before DECIMAL(19,4) NOT NULL,
    stock_after  DECIMAL(19,4) NOT NULL,
    reference_id UUID,
    notes        TEXT,
    created_by   UUID NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_movements_branch    ON stock_movements(branch_id);
CREATE INDEX IF NOT EXISTS idx_movements_product   ON stock_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_movements_variant   ON stock_movements(variant_id);
CREATE INDEX IF NOT EXISTS idx_movements_type      ON stock_movements(type);
CREATE INDEX IF NOT EXISTS idx_movements_reason    ON stock_movements(reason);
CREATE INDEX IF NOT EXISTS idx_movements_created   ON stock_movements(created_at);
CREATE INDEX IF NOT EXISTS idx_movements_reference ON stock_movements(reference_id);


-- =====================================================================
-- TABLA 8: sales — Cabecera de ventas
-- =====================================================================
CREATE TABLE IF NOT EXISTS sales (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number            VARCHAR(50)  NOT NULL UNIQUE,
    branch_id         UUID NOT NULL REFERENCES branches(id),
    seller_id         UUID NOT NULL REFERENCES users(id),
    customer_id       UUID,
    customer_name     VARCHAR(200),
    customer_tax_info JSONB,
    origin            VARCHAR(20)  NOT NULL DEFAULT 'DIRECT'
                      CHECK (origin IN ('DIRECT','ORDER','QUOTATION')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','COMPLETED','CANCELLED','REFUNDED')),
    subtotal          DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,
    total             DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code     VARCHAR(3)   NOT NULL REFERENCES public.currencies(code),
    notes             TEXT,
    completed_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        UUID NOT NULL,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_sales_branch   ON sales(branch_id);
CREATE INDEX IF NOT EXISTS idx_sales_seller   ON sales(seller_id);
CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_status   ON sales(status);
CREATE INDEX IF NOT EXISTS idx_sales_origin   ON sales(origin);
CREATE INDEX IF NOT EXISTS idx_sales_created  ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sales_number   ON sales(number);


-- =====================================================================
-- TABLA 9: sale_items — Detalle de ventas
-- =====================================================================
CREATE TABLE IF NOT EXISTS sale_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id         UUID NOT NULL REFERENCES sales(id),
    product_id      UUID REFERENCES products(id),
    variant_id      UUID REFERENCES product_variants(id),
    product_name    VARCHAR(200)  NOT NULL,
    quantity        DECIMAL(19,4) NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(19,4) NOT NULL,
    unit_cost       DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_rate        DECIMAL(5,2)  NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    subtotal        DECIMAL(19,4) NOT NULL,
    total           DECIMAL(19,4) NOT NULL,
    item_type       VARCHAR(20)   NOT NULL DEFAULT 'PHYSICAL'
                    CHECK (item_type IN ('PHYSICAL','SERVICE','DIGITAL')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale    ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_variant ON sale_items(variant_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_type    ON sale_items(item_type);


-- =====================================================================
-- TABLA 10: sale_payments — Pagos de ventas
-- =====================================================================
CREATE TABLE IF NOT EXISTS sale_payments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id       UUID NOT NULL REFERENCES sales(id),
    method        VARCHAR(20) NOT NULL
                  CHECK (method IN ('CASH','YAPE','PLIN','TRANSFER','CARD','CREDIT','OTHER')),
    amount        DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency_code VARCHAR(3)   NOT NULL REFERENCES public.currencies(code),
    cash_received DECIMAL(19,4),
    change_amount DECIMAL(19,4),
    reference     VARCHAR(100),
    notes         TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by    UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_sale    ON sale_payments(sale_id);
CREATE INDEX IF NOT EXISTS idx_payments_method  ON sale_payments(method);
CREATE INDEX IF NOT EXISTS idx_payments_created ON sale_payments(created_at);


-- =====================================================================
-- TABLA 11: transactions — Movimientos financieros
-- =====================================================================
CREATE TABLE IF NOT EXISTS transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number           VARCHAR(50)  NOT NULL UNIQUE,
    branch_id        UUID NOT NULL REFERENCES branches(id),
    type             VARCHAR(3)   NOT NULL CHECK (type IN ('IN','OUT')),
    category         VARCHAR(30)  NOT NULL
                     CHECK (category IN (
                         'SALE','LOAN_RECEIVED','OWNER_DEPOSIT',
                         'SUPPLIER_RETURN','OTHER_INCOME',
                         'SUPPLIER_PAYMENT','RENT','UTILITIES',
                         'SALARY','TAX_PAYMENT','MAINTENANCE',
                         'MARKETING','LOAN_PAYMENT',
                         'OWNER_WITHDRAWAL','OTHER_EXPENSE'
                     )),
    description      VARCHAR(500) NOT NULL,
    amount           DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency_code    VARCHAR(3)   NOT NULL REFERENCES public.currencies(code),
    payment_method   VARCHAR(20)  NOT NULL DEFAULT 'CASH'
                     CHECK (payment_method IN (
                         'CASH','TRANSFER','YAPE','PLIN','CARD','CHECK','OTHER'
                     )),
    payment_ref      VARCHAR(100),
    sale_id          UUID REFERENCES sales(id),
    supplier_id      UUID,
    voucher_number   VARCHAR(100),
    voucher_url      VARCHAR(500),
    transaction_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    notes            TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by       UUID NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_transactions_branch   ON transactions(branch_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type     ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category);
CREATE INDEX IF NOT EXISTS idx_transactions_date     ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_sale     ON transactions(sale_id);
CREATE INDEX IF NOT EXISTS idx_transactions_supplier ON transactions(supplier_id);


-- =====================================================================
-- TABLA 12: cash_registers — Apertura y cierre de caja
-- =====================================================================
CREATE TABLE IF NOT EXISTS cash_registers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       UUID NOT NULL REFERENCES branches(id),
    opened_by       UUID NOT NULL REFERENCES users(id),
    closed_by       UUID REFERENCES users(id),
    opening_amount  DECIMAL(19,4) NOT NULL DEFAULT 0,
    expected_amount DECIMAL(19,4),
    actual_amount   DECIMAL(19,4),
    difference      DECIMAL(19,4),
    currency_code   VARCHAR(3)   NOT NULL REFERENCES public.currencies(code),
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','CLOSED')),
    opening_notes   TEXT,
    closing_notes   TEXT,
    opened_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cash_branch  ON cash_registers(branch_id);
CREATE INDEX IF NOT EXISTS idx_cash_status  ON cash_registers(status);
CREATE INDEX IF NOT EXISTS idx_cash_opened  ON cash_registers(opened_at);
CREATE INDEX IF NOT EXISTS idx_cash_opener  ON cash_registers(opened_by);


-- =====================================================================
-- TABLA 13: suppliers — Proveedores
-- =====================================================================
CREATE TABLE IF NOT EXISTS suppliers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    legal_name       VARCHAR(200),
    tax_info         JSONB        NOT NULL DEFAULT '{}',
    contact_name     VARCHAR(200),
    email            VARCHAR(255),
    phone            VARCHAR(20),
    whatsapp         VARCHAR(20),
    address          VARCHAR(255),
    city             VARCHAR(100),
    country_code     VARCHAR(2)   REFERENCES public.countries(code),
    payment_days     SMALLINT     NOT NULL DEFAULT 0,
    min_order_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code    VARCHAR(3)   NOT NULL DEFAULT 'PEN'
                     REFERENCES public.currencies(code),
    notes            TEXT,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by       UUID,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_suppliers_name    ON suppliers(name);
CREATE INDEX IF NOT EXISTS idx_suppliers_active  ON suppliers(active);
CREATE INDEX IF NOT EXISTS idx_suppliers_country ON suppliers(country_code);