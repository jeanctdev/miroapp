package com.miroapp.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// Category — entidad que mapea la tabla categories del schema tenant
// =====================================================================
// La tabla categories ya existe — fue creada por tenant-service
// via V1__init_tenant_schema.sql. JPA solo la mapea, no la crea.
//
// Características clave:
//   parent_id → permite categorías jerárquicas:
//               Medicamentos > Antibióticos > Cápsulas
//   sort_order → orden de aparición en el POS
//   deleted_at → soft delete — nunca se usa DELETE físico
//
// @SQLRestriction → Hibernate agrega automáticamente
//   WHERE deleted_at IS NULL en todas las queries.
//   El usuario nunca ve categorías eliminadas.
// =====================================================================
@Entity
@Table(name = "categories")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Nombre de la categoría — requerido
    // Ejemplos: Medicamentos, Alimentos, Accesorios, Servicios
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // Descripción opcional de la categoría
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Categoría padre — permite jerarquía de hasta N niveles
    // NULL → es una categoría raíz (nivel 0)
    // UUID → es una subcategoría
    // Auto-referencial: categories.parent_id → categories.id
    @Column(name = "parent_id")
    private UUID parentId;

    // Orden de aparición en el POS y en listas
    // 0 = primero, 1 = segundo, etc.
    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    // false → oculta en el POS pero conserva el historial
    @Column(name = "active", nullable = false)
    private Boolean active;

    // ── Auditoría ─────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // UUID del usuario que creó la categoría
    // Viene del header X-User-Id propagado por el gateway
    @Column(name = "created_by")
    private UUID createdBy;

    // NULL → activa | fecha → eliminada (soft delete)
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // ── Hooks de JPA ──────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt  = now;
        this.updatedAt  = now;
        if (this.active     == null) this.active     = true;
        if (this.sortOrder  == null) this.sortOrder  = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}