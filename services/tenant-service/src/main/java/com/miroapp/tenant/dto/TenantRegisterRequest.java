package com.miroapp.tenant.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

// =====================================================================
// TenantRegisterRequest — datos que recibe el endpoint de registro
//
// ¿Por qué validaciones aquí y no en la Entity?
// → La Entity representa la BD — no sabe de HTTP
// → El DTO representa lo que el cliente envía
// → @Valid en el Controller activa estas validaciones
//   automáticamente antes de llegar al Service
//
// Si alguna validación falla → Spring retorna 400 Bad Request
// automáticamente con el mensaje de error definido aquí
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegisterRequest {

  // ─── DATOS DE LA EMPRESA ──────────────────────────────────────

  // slug → identificador único del negocio
  // Solo letras minúsculas, números y guiones
  // Ejemplo: "venedog", "farmacia-central", "extintores-peru"
  @NotBlank(message = "{tenant.slug.required}")
  @Size(min = 3, max = 63, message = "{tenant.slug.size}")
  @Pattern(
    regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
    message = "{tenant.slug.pattern}"
  )
  private String slug;

  // Nombre comercial de la empresa
  @NotBlank(message = "{tenant.name.required}")
  @Size(max = 200, message = "{tenant.name.max}")
  private String name;

  // Código ISO del país — PE, CO, MX, VE, CL, AR, EC, BR, US
  @NotBlank(message = "{tenant.country.required}")
  @Size(min = 2, max = 2, message = "{tenant.country.size}")
  private String countryCode;

  // Código ISO de la moneda — PEN, USD, COP, MXN, etc
  @NotBlank(message = "{tenant.currency.required}")
  @Size(min = 3, max = 3, message = "{tenant.currency.size}")
  private String currencyCode;

  // ID del plan elegido — Starter, Business o Enterprise
  @NotNull(message = "{tenant.plan.required}")
  private UUID planId;

// ─── DATOS DEL ADMINISTRADOR ──────────────────────────────────

  // Email del administrador — será su usuario de login
  @NotBlank(message = "{tenant.admin.email.required}")
  @Email(message = "{tenant.admin.email.invalid}")
  @Size(max = 255, message = "{tenant.admin.email.max}")
  private String adminEmail;

  // Nombre completo del administrador
  @NotBlank(message = "{tenant.admin.name.required}")
  @Size(max = 200, message = "{tenant.admin.name.max}")
  private String adminName;

  // Teléfono — opcional
  @Size(max = 20, message = "{tenant.admin.phone.max}")
  private String adminPhone;

  // ─── DATOS OPCIONALES ─────────────────────────────────────────

  // Datos fiscales — varía por país
  // PE: {"type": "RUC", "number": "20123456789"}
  // CO: {"type": "NIT", "number": "900123456-7"}
  // Opcional al registrarse — puede completarse después
  private Map<String, Object> taxInfo;
}
