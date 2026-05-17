-- =====================================================================
-- MIRO ERP — DISEÑO DE BASE DE DATOS
-- Schema: tenant_template
-- Descripción: Tablas de negocio por cada empresa cliente
-- Autor: Jean Paul Cochachin Torres
-- Contacto: jeanct.dev@gmail.com
-- Versión: 1.0.0
-- Fecha: 2026-05-12
-- =====================================================================
--
-- ¿QUÉ ES EL SCHEMA TENANT?
-- Es el espacio privado de cada empresa cliente en Miro.
-- Cuando Venedog se registra, tenant-service crea automáticamente
-- el schema "venedog" y ejecuta estas migraciones con Flyway.
-- Cada empresa tiene su propio schema completamente aislado.
--
-- EN ESTE ARCHIVO:
-- Usamos "tenant_template" como schema ejecutable en PostgreSQL.
-- En producción, tenant-service reemplaza "tenant_template"
-- con el slug real del cliente: venedog, empresa_b, etc.
--
-- ESTRUCTURA COMPLETA:
-- CONFIGURACIÓN: branches, users, categories
-- PRODUCTOS:     products, product_variants
-- INVENTARIO:    stock, stock_movements
-- VENTAS:        sales, sale_items, sale_payments
-- FINANZAS:      transactions, cash_registers
-- PROVEEDORES:   suppliers
--
-- CONVENCIONES:
-- UUID                     → todos los IDs son UUID
-- TIMESTAMP WITH TIME ZONE → todos los timestamps en UTC
-- soft delete              → deleted_at en lugar de DELETE
-- JSONB                    → para datos flexibles
-- Registros inmutables     → stock_movements, sale_items, sale_payments
--
-- TABLAS FUTURAS (cuando un cliente lo pida):
-- employees, payroll   → RRHH y nómina
-- customers            → CRM
-- purchase_orders      → módulo de compras
-- quotations           → módulo de cotizaciones
-- =====================================================================


-- =====================================================================
-- CREAR EL SCHEMA
-- En producción este paso lo hace tenant-service automáticamente
-- con el slug del cliente. Aquí usamos tenant_template para pruebas.
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS tenant_template;


-- =====================================================================
-- TABLA 1: branches — Sucursales
-- =====================================================================
-- ¿Para qué sirve?
-- Registra las sucursales físicas de la empresa.
-- Todo en el sistema está asociado a una sucursal:
-- ventas, stock, usuarios, transacciones financieras.
-- Es la base de la operación multi-sucursal.
--
-- El número máximo de sucursales lo controla el plan:
-- Starter    → max_branches: 1
-- Business   → max_branches: 3
-- Enterprise → ilimitadas
-- El código Java verifica este límite antes de crear una sucursal.
-- =====================================================================
CREATE TABLE tenant_template.branches (

    -- Identificador único de la sucursal
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Nombre visible en reportes y en el POS
    -- Ejemplos: "Sede Central", "Tienda Norte", "Sucursal Miraflores"
    name       VARCHAR(100) NOT NULL,

    -- Dirección física — útil para documentos fiscales
    address    VARCHAR(255),

    -- Distrito o ciudad — permite filtrar reportes por zona
    city       VARCHAR(100),

    -- Teléfono de contacto de la sucursal
    phone      VARCHAR(20),

    -- Email de contacto — puede ser diferente al del administrador
    email      VARCHAR(255),

    -- ¿Es la sucursal principal?
    -- Se crea automáticamente al registrar el tenant
    -- Solo puede haber una principal por tenant
    is_main    BOOLEAN NOT NULL DEFAULT false,

    -- false → no aparece en el POS ni en reportes activos
    -- Los datos históricos se conservan para auditoría
    active     BOOLEAN NOT NULL DEFAULT true,

    -- Auditoría estándar — presente en todas las tablas del tenant
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,

    -- Soft delete — nunca se borra con DELETE
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_branches_active ON tenant_template.branches(active);


-- =====================================================================
-- TABLA 2: users — Usuarios del sistema
-- =====================================================================
-- ¿Para qué sirve?
-- Registra los empleados que tienen acceso al sistema.
-- Cada usuario tiene un rol que define qué puede hacer
-- y a qué sucursal pertenece.
--
-- Roles disponibles:
-- TENANT_ADMIN → acceso total a todas las sucursales
-- MANAGER      → acceso a su sucursal, aprueba devoluciones
-- CASHIER      → solo puede usar el POS de su sucursal
-- VIEWER       → solo lectura, ve reportes pero no opera
--
-- El número máximo de usuarios lo controla el plan:
-- Starter    → max_users: 5  totales
-- Business   → max_users: 15 totales
-- Enterprise → ilimitados
-- =====================================================================
CREATE TABLE tenant_template.users (

    -- Identificador único del usuario
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Email para hacer login — UNIQUE dentro del tenant
    email         VARCHAR(255) NOT NULL UNIQUE,

    -- Contraseña hasheada con BCrypt
    -- NUNCA se guarda en texto plano
    -- Si el usuario olvida su password → se genera uno nuevo
    password_hash VARCHAR(255) NOT NULL,

    -- Nombre completo del empleado
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,

    -- Teléfono — opcional
    phone         VARCHAR(20),

    -- Rol que define qué puede ver y hacer en el sistema
    role          VARCHAR(20) NOT NULL DEFAULT 'CASHIER'
                  CHECK (role IN ('TENANT_ADMIN','MANAGER','CASHIER','VIEWER')),

    -- Sucursal asignada
    -- NULL solo para TENANT_ADMIN — accede a todas las sucursales
    branch_id     UUID REFERENCES tenant_template.branches(id),

    -- URL de foto de perfil — guardada en Azure Blob Storage
    avatar_url    VARCHAR(500),

    -- false → no puede hacer login
    -- Se usa cuando un empleado sale de la empresa
    active        BOOLEAN NOT NULL DEFAULT true,

    -- Fecha del último login exitoso
    -- Útil para detectar usuarios inactivos
    last_login_at TIMESTAMP WITH TIME ZONE,

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

    -- Auditoría estándar
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by    UUID,
    deleted_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_users_email   ON tenant_template.users(email);
CREATE INDEX idx_users_branch  ON tenant_template.users(branch_id);
CREATE INDEX idx_users_role    ON tenant_template.users(role);
CREATE INDEX idx_users_active  ON tenant_template.users(active);


-- =====================================================================
-- TABLA 3: categories — Categorías de productos
-- =====================================================================
-- ¿Para qué sirve?
-- Organiza el catálogo en grupos lógicos.
-- Usa auto-referencia (parent_id → sí misma) para crear
-- un árbol jerárquico sin límite de niveles:
--
-- Alimentos (parent_id: NULL — raíz)
-- └── Alimentos para perros (parent_id: ID de Alimentos)
--     └── Croquetas (parent_id: ID de Alimentos para perros)
-- =====================================================================
CREATE TABLE tenant_template.categories (

    -- Identificador único
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Nombre visible en el catálogo
    name        VARCHAR(100) NOT NULL,

    -- Descripción opcional
    description TEXT,

    -- Categoría padre — NULL si es categoría raíz
    -- Auto-referencia → árbol jerárquico ilimitado
    parent_id   UUID REFERENCES tenant_template.categories(id),

    -- Orden de aparición en la interfaz
    sort_order  SMALLINT NOT NULL DEFAULT 0,

    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by  UUID,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_categories_parent ON tenant_template.categories(parent_id);
CREATE INDEX idx_categories_active ON tenant_template.categories(active);


-- =====================================================================
-- TABLA 4: products — Catálogo de productos padre
-- =====================================================================
-- ¿Para qué sirve?
-- Es el catálogo de productos del negocio.
-- Soporta 3 tipos para cubrir cualquier negocio:
--
-- 'physical' → producto físico tangible
--   Ejemplos: extintor, Royal Canin, polo, medicamento
--   Puede tener stock y variantes
--
-- 'service'  → servicio intangible
--   Ejemplos: instalación, consulta veterinaria, asesoría
--   Sin stock (track_stock siempre false), sin variantes
--
-- 'digital'  → entregable digital
--   Ejemplos: plano en PDF, licencia de software
--   Sin stock físico (track_stock siempre false)
--   Puede tener variantes (AutoCAD vs PDF)
--
-- El límite del plan cuenta productos PADRE — no variantes:
-- Starter    → max_products: 500
-- Business   → max_products: 5,000
-- Enterprise → ilimitados
--
-- REGLAS forzadas por el código Java:
-- si type != 'physical' → track_stock = false automático
-- si type = 'service'   → has_variants = false automático
-- =====================================================================
CREATE TABLE tenant_template.products (

    -- Identificador único del producto
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Categoría — NULL permitido, se puede asignar después
    category_id UUID REFERENCES tenant_template.categories(id),

    -- Nombre visible en el POS y reportes
    name        VARCHAR(200) NOT NULL,

    -- Descripción detallada — aparece en el catálogo
    description TEXT,

    -- Tipo de producto — define el comportamiento completo
    -- physical → stock, puede tener variantes
    -- service  → sin stock, sin variantes
    -- digital  → sin stock físico, puede tener variantes
    type         VARCHAR(20)  NOT NULL DEFAULT 'PHYSICAL'
                 CHECK (type IN ('PHYSICAL','SERVICE','DIGITAL')),

    -- SKU — código interno del negocio
    -- NULL permitido — no todos los negocios usan SKU
    sku         VARCHAR(100) UNIQUE,

    -- Código de barras
    -- SOLO para físicos SIN variantes
    -- Con variantes → el código va en cada variante
    -- service/digital → siempre NULL
    barcode     VARCHAR(100) UNIQUE,

    -- Precio base — para productos SIN variantes
    -- Con variantes → el precio va en cada variante
    -- DECIMAL(19,4) → 4 decimales para máxima precisión
    -- Soporta precios en Venezuela o Argentina sin perder precisión
    base_price  DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Costo de adquisición o producción
    -- Ganancia = base_price - base_cost
    -- Crítico para el módulo de rentabilidad
    base_cost   DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Impuesto aplicable — NULL hereda el del país del tenant
    -- Medicamentos → EXO 0%, Servicios → IGV 18%, etc
    tax_type_id UUID REFERENCES public.tax_types(id),

    -- Unidad de medida
    -- physical → "unidad", "kg", "litro", "metro"
    -- service  → "servicio", "hora", "visita"
    -- digital  → "unidad", "licencia", "archivo"
    unit        VARCHAR(50) NOT NULL DEFAULT 'unidad',

    -- ¿Tiene variantes? (tallas, pesos, formatos, etc)
    -- false → precio y barcode aquí en products
    -- true  → precio y barcode en product_variants
    has_variants BOOLEAN NOT NULL DEFAULT false,

    -- ¿Se controla el stock?
    -- physical + true  → descuenta stock al vender
    -- physical + false → el negocio decide no controlar
    -- service/digital  → siempre false (forzado por Java)
    track_stock BOOLEAN NOT NULL DEFAULT true,

    -- URL de imagen — guardada en Azure Blob Storage
    image_url   VARCHAR(500),

    -- false → no aparece en el POS ni catálogo
    active      BOOLEAN NOT NULL DEFAULT true,

    -- Auditoría estándar
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by  UUID,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_products_name     ON tenant_template.products(name);
CREATE INDEX idx_products_type     ON tenant_template.products(type);
CREATE INDEX idx_products_category ON tenant_template.products(category_id);
CREATE INDEX idx_products_barcode  ON tenant_template.products(barcode);
CREATE INDEX idx_products_sku      ON tenant_template.products(sku);
CREATE INDEX idx_products_active   ON tenant_template.products(active);


-- =====================================================================
-- TABLA 5: product_variants — Variantes de productos
-- =====================================================================
-- ¿Para qué sirve?
-- Guarda cada presentación específica de un producto padre.
-- Solo existe cuando products.has_variants = true.
-- Los servicios NUNCA tienen variantes.
--
-- Ejemplo veterinaria (physical):
-- Royal Canin Adult (padre — has_variants: true)
-- ├── 3kg  → barcode: 7501234000031 → price: S/45 → cost: S/32
-- ├── 8kg  → barcode: 7501234000088 → price: S/98 → cost: S/71
-- └── 15kg → barcode: 7501234000150 → price: S/175 → cost: S/128
--
-- Ejemplo arquitecto (digital):
-- Plano de casa (padre — has_variants: true)
-- ├── AutoCAD → price: S/300 → attributes: {"formato": "dwg"}
-- └── PDF     → price: S/150 → attributes: {"formato": "pdf"}
-- =====================================================================
CREATE TABLE tenant_template.product_variants (

    -- Identificador único de la variante
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Producto padre — NOT NULL, siempre tiene un padre
    product_id UUID NOT NULL REFERENCES tenant_template.products(id),

    -- Nombre descriptivo — qué la diferencia del padre
    -- "3kg", "Talla M / Azul", "Formato AutoCAD"
    name       VARCHAR(200) NOT NULL,

    -- SKU específico de esta variante
    sku        VARCHAR(100) UNIQUE,

    -- Código de barras — SOLO para físicos
    -- digital → siempre NULL
    barcode    VARCHAR(100) UNIQUE,

    -- Precio de esta variante — reemplaza al base_price del padre
    price      DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Costo de esta variante — para rentabilidad por variante
    cost       DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Atributos en JSONB — flexible según el tipo de negocio
    -- Veterinaria:  {"peso": "3kg"}
    -- Ropa:         {"talla": "M", "color": "Azul"}
    -- Farmacia:     {"presentacion": "Tabletas", "cantidad": "20"}
    -- Extintores:   {"capacidad": "6kg", "agente": "PQS"}
    -- Arquitectura: {"formato": "AutoCAD", "version": "2024"}
    attributes JSONB NOT NULL DEFAULT '{}',

    -- Imagen específica — NULL hereda la del padre
    image_url  VARCHAR(500),

    -- Orden de aparición — XS(1) S(2) M(3) L(4) XL(5)
    sort_order SMALLINT NOT NULL DEFAULT 0,

    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_variants_product ON tenant_template.product_variants(product_id);
CREATE INDEX idx_variants_barcode ON tenant_template.product_variants(barcode);
CREATE INDEX idx_variants_sku     ON tenant_template.product_variants(sku);
CREATE INDEX idx_variants_active  ON tenant_template.product_variants(active);


-- =====================================================================
-- TABLA 6: stock — Stock actual por producto/variante/sucursal
-- =====================================================================
-- ¿Para qué sirve?
-- Guarda la cantidad disponible en tiempo real.
-- El POS consulta esta tabla antes de cada venta.
--
-- ¿Quién tiene registro aquí?
-- physical + track_stock = true  → SÍ tiene registro
-- physical + track_stock = false → NO tiene registro
-- service                        → NUNCA tiene registro
-- digital                        → NUNCA tiene registro
--
-- Regla de exclusividad:
-- producto sin variantes → product_id tiene valor, variant_id = NULL
-- producto con variantes → variant_id tiene valor, product_id = NULL
-- NUNCA los dos con valor al mismo tiempo
-- =====================================================================
CREATE TABLE tenant_template.stock (

    -- Identificador único del registro de stock
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sucursal — el stock siempre pertenece a una sucursal
    branch_id       UUID NOT NULL REFERENCES tenant_template.branches(id),

    -- Producto simple (has_variants = false)
    -- NULL si el stock es de una variante
    product_id      UUID REFERENCES tenant_template.products(id),

    -- Variante específica (has_variants = true)
    -- NULL si el stock es de un producto simple
    variant_id      UUID REFERENCES tenant_template.product_variants(id),

    -- Cantidad actual disponible
    -- DECIMAL para productos por peso o volumen:
    -- 2.5 kg de queso, 1.75 litros de aceite
    quantity        DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Cantidad mínima antes de generar alerta
    -- Cuando quantity <= min_quantity → el sistema notifica
    -- Cada sucursal puede tener su propio mínimo
    -- 0 → sin alerta configurada
    min_quantity    DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Cantidad máxima recomendada — NULL sin límite
    max_quantity    DECIMAL(19,4),

    -- Fecha de la última actualización
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID,

    -- Garantiza un solo registro por producto/variante/sucursal
    CONSTRAINT uq_stock_branch_product
        UNIQUE (branch_id, product_id, variant_id)
);

CREATE INDEX idx_stock_branch   ON tenant_template.stock(branch_id);
CREATE INDEX idx_stock_product  ON tenant_template.stock(product_id);
CREATE INDEX idx_stock_variant  ON tenant_template.stock(variant_id);
CREATE INDEX idx_stock_quantity ON tenant_template.stock(quantity);


-- =====================================================================
-- TABLA 7: stock_movements — Historial inmutable de movimientos
-- =====================================================================
-- ¿Para qué sirve?
-- Historial completo de todo lo que le pasó al stock.
-- Es el libro contable del inventario.
--
-- Tipos de movimiento:
-- IN  + purchase        → compraste al proveedor
-- IN  + transfer_in     → recibiste de otra sucursal
-- IN  + adjustment_in   → ajuste positivo (conteo físico)
-- IN  + return_customer → cliente devolvió el producto
-- OUT + sale            → vendiste en el POS
-- OUT + transfer_out    → enviaste a otra sucursal
-- OUT + adjustment_out  → ajuste negativo (merma, vencimiento)
-- OUT + return_supplier → devolviste al proveedor
--
-- REGLA DE ORO:
-- Estos registros NUNCA se modifican ni eliminan.
-- Un error → se crea un movimiento de sentido contrario.
-- Por eso NO tiene updated_at ni deleted_at.
-- =====================================================================
CREATE TABLE tenant_template.stock_movements (

    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sucursal donde ocurrió el movimiento
    -- En transferencias: quien envía registra OUT
    --                    quien recibe registra IN (2 registros)
    branch_id    UUID NOT NULL REFERENCES tenant_template.branches(id),

    -- Misma lógica que stock:
    -- sin variantes → product_id tiene valor
    -- con variantes → variant_id tiene valor
    product_id   UUID REFERENCES tenant_template.products(id),
    variant_id   UUID REFERENCES tenant_template.product_variants(id),

    -- IN → stock sube · OUT → stock baja
    type         VARCHAR(3) NOT NULL CHECK (type IN ('IN','OUT')),

    -- Razón específica del movimiento
    reason       VARCHAR(20)  NOT NULL
                 CHECK (reason IN (
                     'PURCHASE','SALE',
                     'TRANSFER_IN','TRANSFER_OUT',
                     'ADJUSTMENT_IN','ADJUSTMENT_OUT',
                     'RETURN_CUSTOMER','RETURN_SUPPLIER'
                 )),

    -- Cantidad — siempre positivo
    -- El type define si suma o resta
    quantity     DECIMAL(19,4) NOT NULL CHECK (quantity > 0),

    -- Stock antes — para auditoría
    -- IN:  stock_before + quantity = stock_after
    -- OUT: stock_before - quantity = stock_after
    stock_before DECIMAL(19,4) NOT NULL,

    -- Stock después — debe coincidir con stock.quantity
    stock_after  DECIMAL(19,4) NOT NULL,

    -- Documento que originó el movimiento
    -- sale → ID de la venta · purchase → ID de la orden de compra
    -- NULL → ajuste manual sin documento
    reference_id UUID,

    -- Obligatorio para ajustes manuales
    notes        TEXT,

    -- Siempre debe saberse quién movió el stock
    created_by   UUID NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- SIN updated_at NI deleted_at — INMUTABLE
);

CREATE INDEX idx_movements_branch    ON tenant_template.stock_movements(branch_id);
CREATE INDEX idx_movements_product   ON tenant_template.stock_movements(product_id);
CREATE INDEX idx_movements_variant   ON tenant_template.stock_movements(variant_id);
CREATE INDEX idx_movements_type      ON tenant_template.stock_movements(type);
CREATE INDEX idx_movements_reason    ON tenant_template.stock_movements(reason);
CREATE INDEX idx_movements_created   ON tenant_template.stock_movements(created_at);
CREATE INDEX idx_movements_reference ON tenant_template.stock_movements(reference_id);


-- =====================================================================
-- TABLA 8: sales — Cabecera de ventas y pedidos
-- =====================================================================
-- ¿Para qué sirve?
-- El documento principal de cada venta o pedido.
-- Agrupa los productos vendidos y los pagos recibidos.
--
-- Orígenes:
-- 'direct'    → venta directa en el POS (cajero registra y cobra)
-- 'order'     → pedido anticipado (cliente pide, luego paga)
--               solo si el tenant tiene pos_orders activado en settings
-- 'quotation' → cotización aceptada (módulo futuro)
--
-- Estados:
-- 'pending'   → registrada pero no pagada (solo para orders)
-- 'completed' → pagada y completada, stock descontado
-- 'cancelled' → anulada, stock devuelto si aplica
-- 'refunded'  → completada pero luego reembolsada
--
-- Soft delete: las ventas NUNCA se borran — historial fiscal intacto
-- =====================================================================
CREATE TABLE tenant_template.sales (

    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Número legible — configurable por tenant: VEN-2026-0001
    number            VARCHAR(50) NOT NULL UNIQUE,

    -- Sucursal donde ocurrió la venta
    branch_id         UUID NOT NULL REFERENCES tenant_template.branches(id),

    -- Cajero o vendedor que registró la venta
    -- Permite auditoría y comisiones futuras
    seller_id         UUID NOT NULL REFERENCES tenant_template.users(id),

    -- Cliente del CRM — NULL para ventas anónimas (muy común en retail)
    customer_id       UUID,

    -- Nombre para documentos fiscales cuando pide factura
    customer_name     VARCHAR(200),

    -- Datos fiscales — varía por país → JSONB
    -- PE: {"type": "RUC", "number": "20123456789"}
    -- PE: {"type": "DNI", "number": "12345678"}
    customer_tax_info JSONB,

    -- ¿Cómo nació esta venta?
    origin           VARCHAR(20)  NOT NULL DEFAULT 'DIRECT'
                      CHECK (origin IN ('DIRECT','ORDER','QUOTATION')),

    -- Estado actual
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','COMPLETED','CANCELLED','REFUNDED')),

    -- Montos calculados automáticamente al agregar productos
    subtotal          DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Total final = subtotal + tax_amount - discount_amount
    total             DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Moneda de la venta — normalmente la del tenant
    currency_code     VARCHAR(3) NOT NULL REFERENCES public.currencies(code),

    -- Notas internas — solo visibles para el negocio
    notes             TEXT,

    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Fecha exacta en que el cliente pagó
    completed_at      TIMESTAMP WITH TIME ZONE,

    created_by        UUID NOT NULL,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sales_branch   ON tenant_template.sales(branch_id);
CREATE INDEX idx_sales_seller   ON tenant_template.sales(seller_id);
CREATE INDEX idx_sales_customer ON tenant_template.sales(customer_id);
CREATE INDEX idx_sales_status   ON tenant_template.sales(status);
CREATE INDEX idx_sales_origin   ON tenant_template.sales(origin);
CREATE INDEX idx_sales_created  ON tenant_template.sales(created_at);
CREATE INDEX idx_sales_number   ON tenant_template.sales(number);


-- =====================================================================
-- TABLA 9: sale_items — Detalle de cada venta
-- =====================================================================
-- ¿Para qué sirve?
-- Cada línea de producto o servicio de la venta.
-- Una venta tiene una cabecera en sales y múltiples líneas aquí.
--
-- Guarda nombre y precio del MOMENTO de la venta.
-- Si el producto cambia después, el histórico queda intacto.
--
-- INMUTABLE: error → cancelar venta y crear nueva.
-- Sin updated_at ni deleted_at.
-- =====================================================================
CREATE TABLE tenant_template.sale_items (

    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Venta a la que pertenece esta línea
    sale_id         UUID NOT NULL REFERENCES tenant_template.sales(id),

    -- Misma lógica que stock:
    -- sin variantes → product_id tiene valor
    -- con variantes → variant_id tiene valor
    product_id      UUID REFERENCES tenant_template.products(id),
    variant_id      UUID REFERENCES tenant_template.product_variants(id),

    -- Nombre guardado en el momento de la venta
    -- Si luego cambia el nombre del producto → el histórico no cambia
    product_name    VARCHAR(200) NOT NULL,

    -- Cantidad — DECIMAL para productos por peso o volumen
    quantity        DECIMAL(19,4) NOT NULL CHECK (quantity > 0),

    -- Precio guardado en el momento de la venta
    unit_price      DECIMAL(19,4) NOT NULL,

    -- Costo en el momento de la venta — para rentabilidad histórica
    unit_cost       DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Descuento específico de esta línea
    discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Porcentaje de impuesto en el momento de la venta
    -- 18.00 (IGV Perú) · 19.00 (IVA Colombia) · 0.00 (exonerado)
    tax_rate        DECIMAL(5,2) NOT NULL DEFAULT 0,

    -- Monto de impuesto = (unit_price × quantity - discount) × tax_rate/100
    tax_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- subtotal = unit_price × quantity - discount_amount
    subtotal        DECIMAL(19,4) NOT NULL,

    -- total = subtotal + tax_amount
    total           DECIMAL(19,4) NOT NULL,

    -- Tipo guardado en el momento — no depende del producto original
    -- physical → descuenta stock al confirmar
    -- service/digital → no descuenta stock
     item_type       VARCHAR(20)   NOT NULL DEFAULT 'PHYSICAL'
                    CHECK (item_type IN ('PHYSICAL','SERVICE','DIGITAL')),

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- SIN updated_at NI deleted_at — INMUTABLE
);

CREATE INDEX idx_sale_items_sale    ON tenant_template.sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON tenant_template.sale_items(product_id);
CREATE INDEX idx_sale_items_variant ON tenant_template.sale_items(variant_id);
CREATE INDEX idx_sale_items_type    ON tenant_template.sale_items(item_type);


-- =====================================================================
-- TABLA 10: sale_payments — Pagos de cada venta
-- =====================================================================
-- ¿Para qué sirve?
-- Registra cómo se pagó cada venta.
-- Una venta PUEDE tener múltiples pagos al mismo tiempo:
-- Venta S/173 → S/100 efectivo + S/73 Yape → 2 registros aquí
-- Suma de amounts debe = sales.total
--
-- Métodos MVP (todos manuales — cajero confirma):
-- cash     → efectivo, calcula vuelto automáticamente
-- yape     → cajero confirma recepción
-- plin     → cajero confirma recepción
-- transfer → cajero confirma cuando llega
-- card     → por POS físico del banco
-- credit   → crédito interno, cliente queda debiendo
-- other    → otro método, especificado en notes
--
-- Integración automática Yape/Plin → fase posterior
-- INMUTABLE: sin updated_at ni deleted_at
-- =====================================================================
CREATE TABLE tenant_template.sale_payments (

    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Venta a la que pertenece este pago
    sale_id       UUID NOT NULL REFERENCES tenant_template.sales(id),

    -- Método de pago utilizado
    method        VARCHAR(20) NOT NULL
                  CHECK (method IN ('CASH','YAPE','PLIN','TRANSFER','CARD','CREDIT','OTHER')),
 
    -- Monto pagado con este método — siempre positivo
    amount        DECIMAL(19,4) NOT NULL CHECK (amount > 0),

    -- Moneda — puede ser diferente a la de la venta
    -- Ej: venta en soles, cliente paga en dólares
    currency_code VARCHAR(3) NOT NULL REFERENCES public.currencies(code),

    -- Solo aplica cuando method = 'cash'
    -- Cuánto entregó el cliente físicamente
    cash_received DECIMAL(19,4),

    -- Vuelto = cash_received - amount
    -- NULL para métodos distintos al efectivo
    change_amount DECIMAL(19,4),

    -- Referencia del pago externo
    -- yape/plin → número de operación
    -- transfer  → número de transferencia bancaria
    -- card      → código de autorización
    -- cash      → NULL
    reference     VARCHAR(100),

    -- notes obligatorio cuando method = 'other'
    notes         TEXT,

    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by    UUID NOT NULL
    -- SIN updated_at NI deleted_at — INMUTABLE
);

CREATE INDEX idx_payments_sale    ON tenant_template.sale_payments(sale_id);
CREATE INDEX idx_payments_method  ON tenant_template.sale_payments(method);
CREATE INDEX idx_payments_created ON tenant_template.sale_payments(created_at);


-- =====================================================================
-- TABLA 11: transactions — Movimientos financieros
-- =====================================================================
-- ¿Para qué sirve?
-- Registra TODOS los movimientos de dinero — no solo ventas.
-- Base del P&L (Pérdidas y Ganancias) automático.
--
-- P&L = ingresos (IN) - egresos (OUT)
--
-- Categorías IN (dinero que entra):
-- sale            → venta completada (vinculado a sales)
-- loan_received   → préstamo recibido
-- owner_deposit   → aporte del dueño
-- supplier_return → devolución de proveedor
-- other_income    → otro ingreso
--
-- Categorías OUT (dinero que sale):
-- supplier_payment → pago a proveedor
-- rent             → alquiler del local
-- utilities        → luz, agua, internet
-- salary           → sueldos y salarios
-- tax_payment      → pago de impuestos a SUNAT/DIAN/SAT
-- maintenance      → mantenimiento de equipos o local
-- marketing        → publicidad
-- loan_payment     → cuota de préstamo
-- owner_withdrawal → retiro de dinero del dueño
-- other_expense    → otro gasto
-- =====================================================================
CREATE TABLE tenant_template.transactions (

    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Número legible — TRX-2026-0001
    number           VARCHAR(50) NOT NULL UNIQUE,

    -- Sucursal — permite ver finanzas por sucursal separado
    branch_id        UUID NOT NULL REFERENCES tenant_template.branches(id),

    -- IN → dinero entra · OUT → dinero sale
    type             VARCHAR(3) NOT NULL CHECK (type IN ('IN','OUT')),

    -- Categoría del movimiento
    category         VARCHAR(30)  NOT NULL
                     CHECK (category IN (
                         'SALE','LOAN_RECEIVED','OWNER_DEPOSIT',
                         'SUPPLIER_RETURN','OTHER_INCOME',
                         'SUPPLIER_PAYMENT','RENT','UTILITIES',
                         'SALARY','TAX_PAYMENT','MAINTENANCE',
                         'MARKETING','LOAN_PAYMENT',
                         'OWNER_WITHDRAWAL','OTHER_EXPENSE'
                     )),

    -- Descripción obligatoria — el dueño debe saber qué fue
    -- "Alquiler local octubre 2026"
    -- "Sueldo María López octubre 2026"
    description      VARCHAR(500) NOT NULL,

    -- Monto — siempre positivo
    -- El type define la dirección del dinero
    amount           DECIMAL(19,4) NOT NULL CHECK (amount > 0),

    -- Moneda de la transacción
    currency_code    VARCHAR(3) NOT NULL REFERENCES public.currencies(code),

    -- Cómo se realizó el movimiento
    payment_method   VARCHAR(20)  NOT NULL DEFAULT 'CASH'
                     CHECK (payment_method IN (
                         'CASH','TRANSFER','YAPE','PLIN','CARD','CHECK','OTHER'
                     )),

    -- Referencia del pago externo
    payment_ref      VARCHAR(100),

    -- Venta asociada — solo cuando category = 'sale'
    sale_id          UUID REFERENCES tenant_template.sales(id),

    -- Proveedor asociado — cuando category = 'supplier_payment'
    -- FK se activa cuando creemos suppliers
    supplier_id      UUID,

    -- Número del comprobante físico (recibo de alquiler, factura)
    voucher_number   VARCHAR(100),

    -- URL del comprobante escaneado — Azure Blob Storage
    voucher_url      VARCHAR(500),

    -- Fecha real de la transacción
    -- Puede ser diferente a created_at
    -- El dueño puede registrar el lunes una compra del viernes
    -- Los reportes usan transaction_date, no created_at
    transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,

    notes            TEXT,

    -- Las transactions SÍ pueden corregirse (tienen updated_at)
    -- A diferencia de stock_movements y sale_items
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by       UUID NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_transactions_branch   ON tenant_template.transactions(branch_id);
CREATE INDEX idx_transactions_type     ON tenant_template.transactions(type);
CREATE INDEX idx_transactions_category ON tenant_template.transactions(category);
CREATE INDEX idx_transactions_date     ON tenant_template.transactions(transaction_date);
CREATE INDEX idx_transactions_sale     ON tenant_template.transactions(sale_id);
CREATE INDEX idx_transactions_supplier ON tenant_template.transactions(supplier_id);


-- =====================================================================
-- TABLA 12: cash_registers — Apertura y cierre de caja
-- =====================================================================
-- ¿Para qué sirve?
-- Controla el dinero por turno y sucursal.
-- Detecta diferencias entre lo esperado y lo real.
--
-- Flujo:
-- 1. Cajero abre la caja → registra el fondo inicial
-- 2. Durante el turno → se registran ventas y retiros
-- 3. Al cerrar → cajero cuenta el dinero físico
-- 4. Sistema calcula → difference = actual - expected
--    0  → cuadra perfectamente ✅
--    >0 → hay dinero de más (sobrante)
--    <0 → falta dinero (faltante) ⚠️
--
-- INMUTABLE una vez cerrada — sin updated_at ni deleted_at
-- =====================================================================
CREATE TABLE tenant_template.cash_registers (

    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sucursal donde se abrió la caja
    branch_id       UUID NOT NULL REFERENCES tenant_template.branches(id),

    -- Cajero que abrió — NOT NULL, siempre debe saberse quién
    opened_by       UUID NOT NULL REFERENCES tenant_template.users(id),

    -- Cajero que cerró — NULL si aún está abierta
    closed_by       UUID REFERENCES tenant_template.users(id),

    -- Fondo inicial — el dinero en caja antes de empezar el turno
    -- Ejemplo: S/200 para dar vuelto
    opening_amount  DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Calculado automáticamente al cerrar:
    -- opening_amount + ventas efectivo - retiros efectivo
    -- NULL → caja aún abierta
    expected_amount DECIMAL(19,4),

    -- Lo que el cajero contó físicamente al cerrar
    -- NULL → caja aún abierta
    actual_amount   DECIMAL(19,4),

    -- difference = actual_amount - expected_amount
    -- NULL → caja aún abierta
    difference      DECIMAL(19,4),

    -- Moneda de la caja
    currency_code   VARCHAR(3) NOT NULL REFERENCES public.currencies(code),

    -- open → turno activo · closed → turno terminado
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','CLOSED')),

    -- Notas al abrir → "Fondo reducido por pago urgente ayer"
    opening_notes   TEXT,

    -- Notas al cerrar — obligatorio si hay diferencia
    -- "Faltante por error en vuelto de venta #0023"
    closing_notes   TEXT,

    opened_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- NULL → caja aún abierta
    closed_at       TIMESTAMP WITH TIME ZONE,

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- SIN updated_at NI deleted_at — INMUTABLE al cerrar
);

CREATE INDEX idx_cash_branch  ON tenant_template.cash_registers(branch_id);
CREATE INDEX idx_cash_status  ON tenant_template.cash_registers(status);
CREATE INDEX idx_cash_opened  ON tenant_template.cash_registers(opened_at);
CREATE INDEX idx_cash_opener  ON tenant_template.cash_registers(opened_by);


-- =====================================================================
-- TABLA 13: suppliers — Proveedores
-- =====================================================================
-- ¿Para qué sirve?
-- Registra los proveedores del negocio.
-- Nacionales e internacionales.
-- tax_info en JSONB porque la estructura varía por país:
-- PE → {"type": "RUC", "number": "20123456789"}
-- CO → {"type": "NIT", "number": "900123456-7"}
-- Informal → {}
-- =====================================================================
CREATE TABLE tenant_template.suppliers (

    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Nombre comercial — cómo el negocio lo conoce
    name             VARCHAR(200) NOT NULL,

    -- Razón social — puede diferir del nombre comercial
    -- NULL → mismo que name o proveedor informal
    legal_name       VARCHAR(200),

    -- Datos fiscales — varía por país → JSONB
    tax_info         JSONB NOT NULL DEFAULT '{}',

    -- Persona de contacto en el proveedor
    contact_name     VARCHAR(200),

    email            VARCHAR(255),
    phone            VARCHAR(20),

    -- WhatsApp — muy común para pedidos en LATAM
    whatsapp         VARCHAR(20),

    address          VARCHAR(255),
    city             VARCHAR(100),

    -- País del proveedor — puede ser internacional
    country_code     VARCHAR(2) REFERENCES public.countries(code),

    -- Días de crédito que da el proveedor
    -- 0 → pago al contado · 15/30 → días para pagar
    payment_days     SMALLINT NOT NULL DEFAULT 0,

    -- Monto mínimo de pedido — 0 sin mínimo
    min_order_amount DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Moneda en que factura — puede diferir del tenant
    currency_code    VARCHAR(3) NOT NULL DEFAULT 'PEN'
                     REFERENCES public.currencies(code),

    -- "Solo recibe pedidos lunes y miércoles"
    -- "5% descuento en pedidos mayores a S/2,000"
    notes            TEXT,

    -- false → ya no se trabaja con él
    -- El historial de compras anteriores se conserva
    active           BOOLEAN NOT NULL DEFAULT true,

    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by       UUID,
    deleted_at       TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_suppliers_name    ON tenant_template.suppliers(name);
CREATE INDEX idx_suppliers_active  ON tenant_template.suppliers(active);
CREATE INDEX idx_suppliers_country ON tenant_template.suppliers(country_code);


-- =====================================================================
-- RESUMEN FINAL
-- =====================================================================
-- CONFIGURACIÓN: branches · users · categories
-- PRODUCTOS:     products · product_variants
-- INVENTARIO:    stock · stock_movements (INMUTABLE)
-- VENTAS:        sales · sale_items (INMUTABLE) · sale_payments (INMUTABLE)
-- FINANZAS:      transactions · cash_registers (INMUTABLE al cerrar)
-- PROVEEDORES:   suppliers
--
-- TABLAS FUTURAS (cuando un cliente lo pida):
-- RRHH:          employees · payroll · attendance
-- CRM:           customers · customer_contacts
-- COMPRAS:       purchase_orders · purchase_order_items
-- COTIZACIONES:  quotations · quotation_items
-- =====================================================================