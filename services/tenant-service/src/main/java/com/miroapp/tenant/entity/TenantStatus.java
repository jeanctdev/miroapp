package com.miroapp.tenant.entity;

// =====================================================================
// TenantStatus — Estados del ciclo de vida de un tenant
//
public enum TenantStatus {
  // Período de prueba gratuita — 14 días al registrarse
  // Es el estado inicial de todo tenant nuevo
  TRIAL,

  // Suscripción pagada y activa
  // El tenant puede operar normalmente
  ACTIVE,

  // Pago vencido — acceso limitado
  // Puede ver sus datos pero no registrar ventas
  SUSPENDED,

  // Canceló la suscripción
  // Datos conservados 30 días luego se eliminan
  CANCELLED
}
