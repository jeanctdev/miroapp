package com.miroapp.product.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

// =====================================================================
// SecurityUtils — extrae datos del SecurityContext
// =====================================================================
// El JwtAuthenticationFilter guardó en el SecurityContext:
//   principal   = userId (UUID del usuario autenticado)
//   credentials = tenantSlug (schema de PostgreSQL)
//
// Los Services usan SecurityUtils para obtener estos datos
// sin depender del HttpServletRequest — más limpio y testeable.
// =====================================================================
@Component
public class SecurityUtils {
    // UUID del usuario autenticado
    // Viene del header X-User-Id que propagó el gateway
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return UUID.fromString((String) auth.getPrincipal());
    }

    // Slug del tenant activo
    // Viene del header X-Tenant-Slug que propagó el gateway
    // Es el nombre del schema en PostgreSQL
    public String getCurrentTenantSlug() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return (String) auth.getCredentials();
    }
}
