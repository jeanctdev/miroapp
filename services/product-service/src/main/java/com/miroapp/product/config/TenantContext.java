package com.miroapp.product.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// =====================================================================
// TenantContext — establece el schema del tenant en PostgreSQL
// =====================================================================
// MIRO usa schema-per-tenant. Cada empresa tiene su propio schema.
// Antes de cualquier query JPA debemos decirle a PostgreSQL
// en qué schema operar con: SET search_path TO "slug"
//
// ¿Por qué JdbcTemplate y no JPA?
// JPA no tiene un mecanismo nativo para SET search_path.
// JdbcTemplate ejecuta SQL directo — simple y efectivo.
//
// ¿Cuándo se llama?
// Al inicio de cada método del Service que toca la BD.
// El tenantSlug viene del SecurityContext (lo puso el filtro JWT).
//
// Ejemplo de flujo:
//   Gateway propaga X-Tenant-Slug: venedog
//   JwtAuthenticationFilter lo guarda en credentials del Authentication
//   ProductService llama tenantContext.set("venedog")
//   PostgreSQL hace SET search_path TO "venedog"
//   Todas las queries JPA van al schema venedog
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContext {

    private final JdbcTemplate jdbcTemplate;

    // Establece el schema activo para la sesión actual
    // DEBE llamarse al inicio de cada método que accede a la BD
    public void set(String tenantSlug) {
        log.debug("SET search_path TO \"{}\"", tenantSlug);
        jdbcTemplate.execute(
                String.format("SET search_path TO \"%s\"", tenantSlug)
        );
    }
}
