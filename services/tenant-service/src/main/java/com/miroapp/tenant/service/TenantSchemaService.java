package com.miroapp.tenant.service;

import com.miroapp.tenant.exception.SchemaCreationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;

// =====================================================================
// TenantSchemaService — crea el schema del tenant y ejecuta Flyway
//
// Responsabilidades separadas en métodos privados:
// createSchema()   → crea el schema en PostgreSQL
// runMigrations()  → ejecuta Flyway en ese schema
//
// Cada método tiene su propio try-catch con excepción específica
// → Sabemos exactamente dónde falló
// → Logs detallados por operación
// → Excepciones controladas → GlobalExceptionHandler las maneja
// =====================================================================
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaService {

  // DataSource → la conexión a PostgreSQL
  // Spring la inyecta automáticamente desde application-local.yaml
  private final DataSource dataSource;

  // Orquesta la creación del schema completo
  // Se llama desde TenantService al registrar un nuevo tenant
  public void createTenantSchema(String slug) {
    log.info("Iniciando creación de schema para tenant: {}", slug);
    createSchema(slug);
    runMigrations(slug);
    log.info("Schema '{}' creado y migrado exitosamente", slug);
  }

  // ─── CREAR SCHEMA EN POSTGRESQL ───────────────────────────────
  // Ejecuta: CREATE SCHEMA IF NOT EXISTS "empresa1"
  // try-with-resources → cierra la conexión automáticamente
  // SQLException → excepción específica de JDBC
  private void createSchema(String slug) {
    log.debug("Creando schema '{}'", slug);

    try (var connection = dataSource.getConnection();
         var statement = connection.createStatement()) {

      statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\"", slug));
      log.info("Schema '{}' creado exitosamente", slug);

    } catch (SQLException e) {
      log.error("Error al crear schema '{}': {}", slug, e.getMessage());
      throw new SchemaCreationException(slug, e);
    }
  }

  // ─── EJECUTAR MIGRACIONES FLYWAY ──────────────────────────────
  // Configura Flyway dinámicamente para el schema del tenant
  // Ejecuta V1__init_tenant_schema.sql en ese schema
  // Crea las 13 tablas del negocio
  private void runMigrations(String slug) {
    log.debug("Ejecutando migraciones Flyway para schema '{}'", slug);

    try {
      Flyway flyway = Flyway.configure()
        .dataSource(dataSource)
        // Apunta al schema del tenant
        .schemas(slug)
        // Scripts de migración del tenant
        .locations("classpath:db/tenant-service")
        // No falla si el schema ya tiene tablas
        .baselineOnMigrate(true)
        // Historial dentro del schema del tenant
        .table("flyway_schema_history")
        .load();

      flyway.migrate();
      log.info("Migraciones ejecutadas exitosamente para schema '{}'", slug);

    } catch (Exception e) {
      log.error("Error ejecutando migraciones para '{}': {}", slug, e.getMessage());
      throw new SchemaCreationException(slug, "migraciones Flyway", e);
    }
  }
}
