package com.miroapp.auth.repository;

import com.miroapp.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// =====================================================================
// UserRepository — acceso a {tenant}.users
//
// ¿Cómo funciona el multi-tenancy aquí?
// → AuthService ejecuta: SET search_path TO "venedog"
// → Después JPA busca "users" en ese search_path
// → Encuentra venedog.users automáticamente
// → El mismo Repository sirve para TODOS los tenants
//
// ¿Por qué no hardcodeamos el schema?
// → Si ponemos @Table(schema = "venedog") → solo funciona para Venedog
// → Sin schema → JPA usa el search_path actual
// → Funciona para venedog, farmacia, barberia, etc ✅
//
// IMPORTANTE: Siempre llamar setSearchPath() en AuthService
// ANTES de usar cualquier método de este Repository
// =====================================================================
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  // ─── BÚSQUEDAS BÁSICAS ────────────────────────────────────────
  // Estos métodos funcionan después del SET search_path
  // JPA genera automáticamente el SQL correcto

  // Login → buscar usuario activo por email
  // SQL generado: SELECT * FROM users
  //               WHERE email = ? AND deleted_at IS NULL
  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  // Crear usuario → verificar que el email no existe
  // SQL generado: SELECT COUNT(*) > 0 FROM users
  //               WHERE email = ? AND deleted_at IS NULL
  boolean existsByEmailAndDeletedAtIsNull(String email);

  // Listar usuarios → solo TENANT_ADMIN y MANAGER
  // SQL generado: SELECT * FROM users
  //               WHERE deleted_at IS NULL
  //               ORDER BY created_at DESC
  List<User> findByDeletedAtIsNullOrderByCreatedAtDesc();

  // ─── QUERIES DE SEGURIDAD ─────────────────────────────────────
  // Incrementar contador de intentos fallidos
  // Se llama cada vez que el login falla
  @Modifying
  @Query("UPDATE User u " +
    "SET u.failedAttempts = u.failedAttempts + 1 " +
    "WHERE u.id = :userId")
  void incrementFailedAttempts(@Param("userId") UUID userId);

  // Resetear intentos fallidos al hacer login exitoso
  // También actualiza el último login
  @Modifying
  @Query("UPDATE User u " +
    "SET u.failedAttempts = 0, " +
    "u.lockedUntil = null, " +
    "u.lastLoginAt = :loginTime " +
    "WHERE u.id = :userId")
  void resetFailedAttemptsAndUpdateLogin(@Param("userId") UUID userId, @Param("loginTime") OffsetDateTime loginTime);

  // Bloquear la cuenta por 15 minutos
  // Se llama cuando failed_attempts llega a 5
  @Modifying
  @Query("UPDATE User u " +
    "SET u.lockedUntil = :lockedUntil " +
    "WHERE u.id = :userId")
  void lockAccount(
    @Param("userId") UUID userId,
    @Param("lockedUntil") OffsetDateTime lockedUntil);

  // Actualizar contraseña al cambiarla
  // También resetea must_change_password, failed_attempts
  // y locked_until por seguridad
  @Modifying
  @Query("UPDATE User u " +
    "SET u.passwordHash = :passwordHash, " +
    "u.mustChangePassword = false, " +
    "u.passwordChangedAt = :changedAt, " +
    "u.failedAttempts = 0, " +
    "u.lockedUntil = null " +
    "WHERE u.id = :userId")
  void updatePassword(
    @Param("userId") UUID userId,
    @Param("passwordHash") String passwordHash,
    @Param("changedAt") OffsetDateTime changedAt);

}
