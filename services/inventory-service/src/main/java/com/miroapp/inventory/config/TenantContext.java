package com.miroapp.inventory.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// =====================================================================
// TenantContext — establece el schema del tenant en cada request
// =====================================================================
// ¿Por qué necesitamos esto?
//   inventory-service opera sobre stock y stock_movements del tenant.
//   Sin SET search_path → PostgreSQL busca en schema public
//   y no encuentra las tablas → error.
//
// ¿Cuándo se llama?
//   Al inicio de CADA método del service que toca la BD.
//   Convención MIRO: primera línea de cada método del service.
//
// ¿Por qué JdbcTemplate?
//   Wrapper de Spring sobre JDBC. Maneja el pool de HikariCP.
//   SET search_path afecta solo la conexión actual del pool.
//   Es la forma más simple y directa de cambiar el schema.
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContext {
  private final JdbcTemplate jdbcTemplate;

  public void set(String tenantSlug) {
    String sql = String.format(
      "SET search_path TO \"%s\"", tenantSlug);
    jdbcTemplate.execute(sql);
    log.debug("SET search_path TO \"{}\"", tenantSlug);
  }
}
