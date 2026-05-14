package com.miroapp.tenant.repository;

import com.miroapp.tenant.entity.Tenant;
import com.miroapp.tenant.entity.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  // ─── BÚSQUEDAS BÁSICAS ────────────────────────────────────────

  // Buscar tenant por slug — el identificador único del negocio
  // Se usa al hacer login para identificar la empresa
  // SQL generado: SELECT * FROM tenants WHERE slug = ? AND deleted_at IS NULL
  Optional<Tenant> findBySlugAndDeletedAtIsNull(String slug);


  // Buscar por email del administrador
  // Se usa para verificar que el email no está registrado ya
  // SQL generado: SELECT * FROM tenants WHERE admin_email = ?
  Optional<Tenant> findByAdminEmail(String adminEmail);

  // ─── VERIFICACIONES DE UNICIDAD ───────────────────────────────
  // Se usan antes de crear un tenant para validar duplicados

  // ¿Ya existe un tenant con este slug?
  // SQL generado: SELECT COUNT(*) > 0 FROM tenants WHERE slug = ?
  boolean existsBySlug(String slug);

  // ¿Ya existe un tenant con este email de admin?
  // SQL generado: SELECT COUNT(*) > 0 FROM tenants WHERE admin_email = ?
  boolean existsByAdminEmail(String adminEmail);

  // ─── BÚSQUEDAS POR STATUS ─────────────────────────────────────
  // Se usan en los jobs automáticos de Sprint 6

  // Todos los tenants activos — para reportes internos de Miro
  List<Tenant> findByStatusAndDeletedAtIsNull(TenantStatus status);

  // ─── QUERY PERSONALIZADA ──────────────────────────────────────
  // Cuando el nombre del método no es suficiente
  // usamos @Query con JPQL (Java Persistence Query Language)
  // JPQL usa nombres de clases y campos Java — no SQL directo

  // Buscar tenant activo por slug — el más usado en cada request
  // Excluye soft deleted (deleted_at IS NULL)
  @Query("SELECT t FROM Tenant t WHERE t.slug = :slug " +
    "AND t.deletedAt IS NULL " +
    "AND t.status != 'CANCELLED'")
  Optional<Tenant> findActiveTenantBySlug(String slug);
}
