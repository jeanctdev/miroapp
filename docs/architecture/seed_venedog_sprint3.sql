-- =====================================================================
-- MIRO ERP — Seed Data: Tenant Venedog
-- Archivo: seed_venedog.sql
-- Version: Sprint 3
-- Descripcion: Data real de prueba para el tenant Venedog
--              Veterinaria de ejemplo en Peru
--
-- USO:
--   1. Asegurarse que el schema venedog existe
--   2. Ejecutar en DBeaver o psql:
--      SET search_path TO venedog;
--      \i seed_venedog.sql
--
-- CONTENIDO:
--   - 17 categorias (5 raices + 12 subcategorias)
--   - 15 productos (10 PHYSICAL, 4 SERVICE, 1 DIGITAL)
--   - 4 variantes del producto "Collar Nylon Ajustable"
--
-- NOTAS:
--   - UUIDs fijos para que sean reproducibles entre entornos
--   - Los UUIDs del usuario createdBy son del admin de venedog
--   - Ejecutar SOLO en un schema venedog vacio o recien creado
-- =====================================================================

SET search_path TO venedog;

-- =====================================================================
-- PASO 0: SUCURSAL PRINCIPAL
-- =====================================================================
-- UUID fijo para reproducibilidad entre entornos.
-- Mismo UUID insertado manualmente en el fix de venedog.
-- Usado como {{branch_id}} en las pruebas Postman.
-- =====================================================================
INSERT INTO branches (
    id,
    name,
    is_main,
    active,
    created_at,
    updated_at
)
VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'Sede Principal',
    true,
    true,
    NOW(),
    NOW()
);

-- =====================================================================
-- PASO 1: CATEGORIAS RAIZ
-- =====================================================================

INSERT INTO categories (id, name, description, parent_id, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('9c2c5d8a-15cd-43ed-ad02-bd6630d0ee05', 'Alimentos',              'Alimentos y nutricion para mascotas',         NULL, 1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('adfb3a61-7e44-4d95-a7dc-0d99b5d88d23', 'Medicamentos',           'Medicamentos veterinarios para mascotas',      NULL, 2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('7a27db49-6ef0-41fd-af6f-88e457e23cf9', 'Accesorios',             'Accesorios y complementos para mascotas',     NULL, 3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('a09930da-7d11-49e6-8016-fe7dd7c2e486', 'Servicios Veterinarios', 'Servicios medicos y esteticos para mascotas', NULL, 4, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- PASO 2: SUBCATEGORIAS
-- =====================================================================

-- Hijos de Alimentos
INSERT INTO categories (id, name, description, parent_id, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('b0690641-feb9-4cb9-b25a-6b92c35ae06f', 'Alimento Seco',          'Croquetas y balanceado seco para perros y gatos',    '9c2c5d8a-15cd-43ed-ad02-bd6630d0ee05', 1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('9b105203-b6ad-4f9b-80e0-b0cc83f0ec58', 'Alimento Humedo',        'Latas y sobres para perros y gatos',                 '9c2c5d8a-15cd-43ed-ad02-bd6630d0ee05', 2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('491b1009-62cf-40e9-bb75-c5868f40ca71', 'Snacks y Premios',       'Premios y golosinas para entrenamiento',             '9c2c5d8a-15cd-43ed-ad02-bd6630d0ee05', 3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Hijos de Medicamentos
INSERT INTO categories (id, name, description, parent_id, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('792e6b7d-8a28-417d-82d8-d4ee234f8da5', 'Antibioticos',           'Antibioticos para perros y gatos',                  'adfb3a61-7e44-4d95-a7dc-0d99b5d88d23', 1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('2d48454d-026f-4692-89bc-2ff1366de357', 'Antiparasitarios',       'Antipulgas, antigarrapatas y desparasitantes',       'adfb3a61-7e44-4d95-a7dc-0d99b5d88d23', 2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('8190d214-86f5-4caa-a767-5d106165ec69', 'Vitaminas y Suplementos','Suplementos nutricionales y vitaminas',              'adfb3a61-7e44-4d95-a7dc-0d99b5d88d23', 3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Hijos de Accesorios
INSERT INTO categories (id, name, description, parent_id, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('389531e5-9514-4a08-9926-1eb93334739e', 'Collares y Correas',     'Collares, correas y arneses para mascotas',          '7a27db49-6ef0-41fd-af6f-88e457e23cf9', 1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('6cf4fce0-d817-4079-88a7-e780f4d28a6d', 'Juguetes',               'Juguetes y entretenimiento para mascotas',           '7a27db49-6ef0-41fd-af6f-88e457e23cf9', 2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('1083bf06-8058-47b1-bc57-73b3a6138333', 'Camas y Casas',          'Camas, casas y espacios de descanso',                '7a27db49-6ef0-41fd-af6f-88e457e23cf9', 3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Hijos de Servicios Veterinarios
INSERT INTO categories (id, name, description, parent_id, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('77f73184-ea51-4682-a5e0-ae82fc7cb4e3', 'Consultas Medicas',      'Consultas generales y especializadas',               'a09930da-7d11-49e6-8016-fe7dd7c2e486', 1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('0c2fa3e9-ba3a-46b7-b5f6-826e13fb2491', 'Cirugia y Procedimientos','Intervenciones quirurgicas y procedimientos',       'a09930da-7d11-49e6-8016-fe7dd7c2e486', 2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('df1fd2f8-4885-4a3c-ae47-07c78cca2a4d', 'Grooming',               'Bano, corte y estetica para mascotas',               'a09930da-7d11-49e6-8016-fe7dd7c2e486', 3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('4d8e2d5d-b95e-411a-8777-b5c371c11e7b', 'Vacunacion',             'Vacunas y plan de inmunizacion',                     'a09930da-7d11-49e6-8016-fe7dd7c2e486', 4, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- PASO 3: PRODUCTOS PHYSICAL
-- =====================================================================

-- Alimento Seco
INSERT INTO products (id, category_id, name, description, type, sku, barcode, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('b99c42fa-4c8a-47ab-bee6-a3ff41f30fee', 'b0690641-feb9-4cb9-b25a-6b92c35ae06f', 'Royal Canin Medium Adult 15kg',  'Alimento balanceado para perros medianos adultos',      'PHYSICAL', 'ALI-RC-MED-15',   '3182550402170', 285.00, 220.00, 'bolsa',  false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('2a0446ff-9ba6-4d64-9983-cbba9193d023', 'b0690641-feb9-4cb9-b25a-6b92c35ae06f', 'Purina Dog Chow Adulto 8kg',    'Alimento completo para perros adultos todas las razas', 'PHYSICAL', 'ALI-PDC-8KG',     '7702223012345',  95.00,  72.00, 'bolsa',  false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Alimento Humedo
INSERT INTO products (id, category_id, name, description, type, sku, barcode, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('c078f9fa-9f5f-4319-8197-2fb469df65af', '9b105203-b6ad-4f9b-80e0-b0cc83f0ec58', 'Whiskas Atun Lata 400g',        'Alimento humedo para gatos adultos sabor atun',         'PHYSICAL', 'ALI-WHI-ATU-400', '5900951253881',   8.50,   6.00, 'lata',   false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Antibioticos
INSERT INTO products (id, category_id, name, description, type, sku, barcode, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('b272f65c-f952-4d68-8664-f8749e5159aa', '792e6b7d-8a28-417d-82d8-d4ee234f8da5', 'Amoxicilina 500mg x 10 capsulas', 'Antibiotico de amplio espectro para perros y gatos',    'PHYSICAL', 'MED-AMOX-500-10', '7750112000001',  18.50,  12.00, 'caja',   false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('ba5cb253-5a6b-4fad-915e-962e3ef33977', '792e6b7d-8a28-417d-82d8-d4ee234f8da5', 'Enrofloxacina 50mg x 10 tabletas','Antibiotico para infecciones bacterianas en mascotas', 'PHYSICAL', 'MED-ENRO-50-10',  '7750112000002',  22.00,  15.00, 'caja',   false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Antiparasitarios
INSERT INTO products (id, category_id, name, description, type, sku, barcode, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('1b479cda-5542-4185-9c6c-b1e9653f07ff', '2d48454d-026f-4692-89bc-2ff1366de357', 'Frontline Plus Perro Grande',   'Antipulgas y antigarrapatas para perros de 20 a 40kg',  'PHYSICAL', 'MED-FRON-PG',     '3661103021234',  65.00,  48.00, 'pipeta', false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('54125240-039a-41ad-82e0-7dd65d14ed0d', '2d48454d-026f-4692-89bc-2ff1366de357', 'Drontal Plus x 2 tabletas',     'Desparasitante interno para perros',                    'PHYSICAL', 'MED-DRON-2TAB',   '7750112000003',  28.00,  19.00, 'caja',   false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Collares y Correas (Padre con variantes)
INSERT INTO products (id, category_id, name, description, type, sku, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('85003d85-a5c8-46a1-bb16-95a773f84519', '389531e5-9514-4a08-9926-1eb93334739e', 'Collar Nylon Ajustable',        'Collar de nylon ajustable para perros',                 'PHYSICAL', 'ACC-COL-NYL',     25.00,  14.00, 'unidad', true,  true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('d8414815-c181-48c4-86db-bd08e5397669', '389531e5-9514-4a08-9926-1eb93334739e', 'Correa Retractil 5 metros',     'Correa retractil para paseos hasta 25kg',               'PHYSICAL', 'ACC-COR-RET-5M',  55.00,  38.00, 'unidad', false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- Jugetes
INSERT INTO products (id, category_id, name, description, type, sku, barcode, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('58a4ba94-e65d-4191-a756-d0c2b18df255', '6cf4fce0-d817-4079-88a7-e780f4d28a6d', 'Pelota Kong Classic Talla M',   'Juguete interactivo resistente para perros medianos',   'PHYSICAL', 'JUG-KONG-CL-M',   '3500940012345',  45.00,  30.00, 'unidad', false, true, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- PASO 4: PRODUCTOS SERVICE
-- =====================================================================

INSERT INTO products (id, category_id, name, description, type, sku, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('886412ef-c206-4222-909b-06f852c9cdc6', '77f73184-ea51-4682-a5e0-ae82fc7cb4e3', 'Consulta General',              'Consulta medica general con el veterinario',            'SERVICE', 'SRV-CONS-GEN',    80.00,   0.00, 'consulta', false, false, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('4fdc2694-936f-4d8a-aad3-e3f932e45140', '77f73184-ea51-4682-a5e0-ae82fc7cb4e3', 'Consulta de Urgencia',          'Atencion de urgencia fuera de horario regular',         'SERVICE', 'SRV-CONS-URG',   150.00,   0.00, 'consulta', false, false, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('0856537e-ff6f-4ea2-a32b-4f9d92e2ef38', 'df1fd2f8-4885-4a3c-ae47-07c78cca2a4d', 'Bano y Corte Perro Mediano',   'Servicio completo de bano, secado y corte',             'SERVICE', 'SRV-GROOM-PM',    60.00,   0.00, 'servicio', false, false, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('5e56ed79-97f5-4e1f-8775-bd823120b0cd', '4d8e2d5d-b95e-411a-8777-b5c371c11e7b', 'Vacuna Antirrabica',           'Vacunacion antirrabica obligatoria para perros y gatos','SERVICE', 'SRV-VAC-RAB',     45.00,  20.00, 'dosis',    false, false, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- PASO 5: PRODUCTOS DIGITAL
-- =====================================================================

INSERT INTO products (id, category_id, name, description, type, sku, base_price, base_cost, unit, has_variants, track_stock, active, created_by, created_at, updated_at)
VALUES
  ('371b8665-1046-4001-a72c-71578aa77b90', NULL, 'Plan Nutricional Personalizado PDF', 'Plan nutricional personalizado en PDF', 'DIGITAL', 'DIG-PLAN-NUT', 35.00, 0.00, 'archivo', false, false, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- PASO 6: VARIANTES DEL COLLAR NYLON AJUSTABLE
-- =====================================================================
-- Producto padre: 85003d85 (Collar Nylon Ajustable)
-- Talla S y M: heredan precio del padre (25.00)
-- Talla L y XL: tienen precio propio

INSERT INTO product_variants (id, product_id, name, sku, barcode, price, cost, attributes, sort_order, active, created_by, created_at, updated_at)
VALUES
  ('1e7d0ec0-971a-4845-9e89-3d89753b38b5', '85003d85-a5c8-46a1-bb16-95a773f84519', 'Talla S',  'ACC-COL-NYL-S',  '7750112000010', NULL,  NULL,  '{"talla": "S"}',  1, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('68f29104-8ab1-42e4-adbd-45a8d848e872', '85003d85-a5c8-46a1-bb16-95a773f84519', 'Talla M',  'ACC-COL-NYL-M',  '7750112000011', NULL,  NULL,  '{"talla": "M"}',  2, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('e36cc72d-6b19-4ac4-884d-a8289727f676', '85003d85-a5c8-46a1-bb16-95a773f84519', 'Talla L',  'ACC-COL-NYL-L',  '7750112000012', 30.00, 17.00, '{"talla": "L"}',  3, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW()),
  ('c2fb9a5e-e78d-4d90-a5a0-fcf713753cb4', '85003d85-a5c8-46a1-bb16-95a773f84519', 'Talla XL', 'ACC-COL-NYL-XL', '7750112000013', 35.00, 20.00, '{"talla": "XL"}', 4, true, 'a48ea628-ccb3-4711-ac51-9294a90bcb57', NOW(), NOW());

-- =====================================================================
-- VERIFICACION FINAL
-- =====================================================================

SELECT 'CATEGORIAS RAIZ'       AS tipo, COUNT(*) AS total FROM categories WHERE parent_id IS NULL AND active = true
UNION ALL
SELECT 'SUBCATEGORIAS',               COUNT(*) FROM categories WHERE parent_id IS NOT NULL AND active = true
UNION ALL
SELECT 'PRODUCTOS PHYSICAL',          COUNT(*) FROM products WHERE type = 'PHYSICAL' AND active = true
UNION ALL
SELECT 'PRODUCTOS SERVICE',           COUNT(*) FROM products WHERE type = 'SERVICE'  AND active = true
UNION ALL
SELECT 'PRODUCTOS DIGITAL',           COUNT(*) FROM products WHERE type = 'DIGITAL'  AND active = true
UNION ALL
SELECT 'VARIANTES',                   COUNT(*) FROM product_variants WHERE active = true;

