package com.miroapp.auth.entity;

// =====================================================================
// Role — Roles disponibles en Miro ERP
//
// Coincide exactamente con CHECK constraint en BD:
// CHECK (role IN ('TENANT_ADMIN','MANAGER','CASHIER','VIEWER'))
//
// TENANT_ADMIN → dueño del negocio — acceso total
// MANAGER      → encargado de sucursal — acceso a su sucursal
// CASHIER      → cajero/vendedor — solo POS
// VIEWER       → contador/socio — solo lectura
// =====================================================================
public enum Role {
  // Dueño del negocio — acceso total a todas las sucursales
  // Es el primer usuario creado al registrar el tenant
  TENANT_ADMIN,

  // Encargado de sucursal — acceso completo a SU sucursal
  // No puede ver otras sucursales ni crear usuarios
  MANAGER,

  // Cajero o vendedor — solo puede operar el POS
  // Registra ventas y cobra en su sucursal asignada
  CASHIER,

  // Contador o socio — solo lectura
  // Ve reportes y finanzas pero no puede operar
  VIEWER
}
