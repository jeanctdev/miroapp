package com.miroapp.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// User — entidad que mapea {tenant}.users
//
// ¿Por qué no tiene @Table con schema fijo?
// → Cada tenant tiene su propio schema
// → El schema se configura dinámicamente en cada request
// → auth-service cambia el search_path de PostgreSQL
//   según el tenant del request
//
// ¿Qué es search_path?
// → Es una variable de PostgreSQL que define en qué schema buscar
// → SET search_path TO empresa → busca en empresa.*
// → Así el mismo query funciona para cualquier tenant
// =====================================================================
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  // Email único dentro del schema del tenant
  // Dos tenants pueden tener el mismo email
  // pero dentro de un tenant es único
  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  // Password hasheado con BCrypt
  // NUNCA se guarda el password en texto plano
  // BCrypt genera un hash diferente cada vez
  // aunque el password sea el mismo
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "phone", length = 20)
  private String phone;

  // Rol del usuario — define sus permisos en el sistema
  // @Enumerated(STRING) → guarda "TENANT_ADMIN", "MANAGER", etc
  // Coincide con CHECK constraint en BD
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  // Sucursal asignada al usuario
  // NULL para TENANT_ADMIN → acceso a todas las sucursales
  // Obligatorio para MANAGER, CASHIER, VIEWER
  @Column(name = "branch_id")
  private UUID branchId;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  // Si false → el usuario no puede hacer login
  // El TENANT_ADMIN puede desactivar usuarios
  @Column(name = "active", nullable = false)
  private boolean active;

  // Última vez que el usuario hizo login
  // Útil para detectar usuarios inactivos
  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  // ─── SEGURIDAD ────────────────────────────────────────────────
  // true  → debe cambiar su contraseña al próximo login
  // false → ya cambió su contraseña
  // Se asigna true al crear el usuario por primera vez
  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  // Contador de intentos fallidos de login consecutivos
  // Se resetea a 0 al hacer login exitoso
  // Al llegar a 5 → se bloquea la cuenta
  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  // Hasta cuándo está bloqueada la cuenta
  // NULL    → cuenta activa sin bloqueo
  // fecha   → bloqueada hasta esa fecha/hora
  // Bloqueo de 15 minutos al llegar a 5 intentos
  @Column(name = "locked_until")
  private OffsetDateTime lockedUntil;

  // Cuándo cambió su contraseña por última vez
  // NULL → nunca la ha cambiado (contraseña temporal)
  // fecha → fecha del último cambio
  @Column(name = "password_changed_at")
  private OffsetDateTime passwordChangedAt;

  // ─── AUDITORÍA ────────────────────────────────────────────────
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // Quién creó este usuario
  @Column(name = "created_by")
  private UUID createdBy;

  // ─── SOFT DELETE ──────────────────────────────────────────────
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  // ─── LIFECYCLE CALLBACKS ──────────────────────────────────────
  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    this.active = true;
    this.mustChangePassword = true;
    this.failedAttempts = 0;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  // ─── HELPER ───────────────────────────────────────────────────
  // Nombre completo del usuario
  public String getFullName() {
    return firstName + " " + lastName;
  }
}
