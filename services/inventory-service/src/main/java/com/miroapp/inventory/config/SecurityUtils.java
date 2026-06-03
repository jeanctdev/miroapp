package com.miroapp.inventory.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

// =====================================================================
// SecurityUtils — extrae datos del JWT del SecurityContext
// =====================================================================
// ¿Cómo llegan estos datos?
//   1. Gateway valida el JWT y propaga headers:
//      X-User-Id, X-Tenant-Slug, X-User-Role
//   2. JwtAuthenticationFilter (security-lib) los lee
//      y puebla el SecurityContext:
//        principal   = userId (String)
//        credentials = tenantSlug (String)
//        authorities = [ROLE_TENANT_ADMIN]
//
// Convención MIRO: el service obtiene userId y tenantSlug
// internamente — el controller NO los pasa como parámetros.
// =====================================================================
@Component
public class SecurityUtils {
  // UUID del usuario autenticado — para auditoría
  public UUID getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString((String) auth.getPrincipal());
  }

  // Slug del tenant — para SET search_path
  // Es el nombre del schema en PostgreSQL
  public String getCurrentTenantSlug() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (String) auth.getCredentials();
  }
}
