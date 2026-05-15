package com.miroapp.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// =====================================================================
// Tenant — entidad que mapea la tabla public.tenants
//
// @Entity → le dice a JPA que esta clase es una tabla en la BD
// @Table  → especifica el nombre exacto de la tabla y su schema
//
// Lombok annotations:
// @Data           → genera getters, setters, equals, hashCode, toString
// @Builder        → patrón Builder para crear objetos limpiamente
// @NoArgsConstructor  → constructor vacío (JPA lo requiere obligatoriamente)
// @AllArgsConstructor → constructor con todos los campos
// =====================================================================
@Entity
@Table(name = "tenants", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

  // ─── PRIMARY KEY ──────────────────────────────────────────────
  // @Id → marca este campo como clave primaria
  // @GeneratedValue(UUID) → PostgreSQL genera el UUID automáticamente
  // updatable = false → el ID nunca se puede cambiar después de creado
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  // ─── IDENTIFICADOR ÚNICO DEL NEGOCIO ──────────────────────────
  // slug → nombre único de la empresa en el sistema
  // Se usa como nombre del schema en PostgreSQL: venedog, empresa_b
  // Se usa en la URL: app.miro.com/venedog
  // Solo letras minúsculas, números y guiones
  @Column(name = "slug", nullable = false, unique = true, length = 63)
  private String slug;

  // ─── DATOS DE LA EMPRESA ──────────────────────────────────────
  @Column(name = "name", nullable = false, length = 200)
  private String name;

  // Código ISO 3166-1 alpha-2 del país
  // PE, CO, MX, VE, CL, AR, EC, BR, US
  // Referencia a public.countries(code)
  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  // Código ISO 4217 de la moneda principal
  // PEN, USD, COP, MXN, VES, CLP, ARS, BRL, EUR
  // Referencia a public.currencies(code)
  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  // ID del plan activo del tenant
  // Referencia a public.plans(id)
  // Por ahora UUID simple — en el futuro @ManyToOne con Plan entity
  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  // ─── DATOS DEL ADMINISTRADOR ──────────────────────────────────
  // El admin es el primer usuario creado al registrar el tenant
  // Su email es único en todo el sistema Miro
  @Column(name = "admin_email", nullable = false, unique = true, length = 255)
  private String adminEmail;

  @Column(name = "admin_name", nullable = false, length = 200)
  private String adminName;

  // Teléfono opcional del administrador
  @Column(name = "admin_phone", length = 20)
  private String adminPhone;

  // ─── DATOS FISCALES ───────────────────────────────────────────
  // JSONB en PostgreSQL → Map<String, Object> en Java
  // Estructura varía por país:
  // PE → {"type": "RUC", "number": "20123456789"}
  // CO → {"type": "NIT", "number": "900123456-7"}
  // MX → {"type": "RFC", "number": "VEN123456ABC"}
  // @JdbcTypeCode(SqlTypes.JSON) → Hibernate serializa/deserializa JSON
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tax_info", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Object> taxInfo = new HashMap<>();

  // ─── CONFIGURACIONES DEL TENANT ───────────────────────────────
  // Configuraciones personalizadas en JSONB
  // timezone_override, logo_url, primary_color, etc
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, Object> settings = new HashMap<>();

  // ─── STATUS ───────────────────────────────────────────────────
  // @Enumerated(EnumType.STRING) → guarda el nombre del Enum en BD
  // TRIAL → guarda "TRIAL" en PostgreSQL
  // Coincide con CHECK (status IN ('TRIAL','ACTIVE','SUSPENDED','CANCELLED'))
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TenantStatus status;

  // Fecha en que vence el período de prueba
  // NULL → ya pasó del trial a un plan pagado
  @Column(name = "trial_ends_at")
  private OffsetDateTime trialEndsAt;

  // ─── AUDITORÍA ────────────────────────────────────────────────
  // updatable = false → created_at se asigna una vez y nunca cambia
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // ─── SOFT DELETE ──────────────────────────────────────────────
  // NULL → tenant activo
  // Con fecha → fue eliminado — datos conservados para auditoría
  // Queries siempre filtran: WHERE deleted_at IS NULL
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  // ─── LIFECYCLE CALLBACKS ──────────────────────────────────────
  // @PrePersist → se ejecuta automáticamente antes del INSERT
  // Asigna created_at, updated_at y status inicial
  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.status == null) {
      this.status = TenantStatus.TRIAL;
    }
    if (this.trialEndsAt == null) {
      // El trial VENCE el día 15 a las 00:00
      // El job del día 15 a las 00:01 lo detecta y suspende
      this.trialEndsAt = now.plusDays(14);
    }
  }

  // @PreUpdate → se ejecuta automáticamente antes del UPDATE
  // Actualiza updated_at para saber cuándo fue la última modificación
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

}
