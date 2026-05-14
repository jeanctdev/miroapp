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
  @NotBlank(message = "El slug es obligatorio")
  @Size(min = 3, max = 63, message = "El slug debe tener entre 3 y 63 caracteres")
  @Pattern(
    regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
    message = "El slug solo puede contener letras minúsculas, números y guiones"
  )
  private String slug;

  // Nombre comercial de la empresa
  @NotBlank(message = "El nombre de la empresa es obligatorio")
  @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
  private String name;

  // Código ISO del país — PE, CO, MX, VE, CL, AR, EC, BR, US
  @NotBlank(message = "El país es obligatorio")
  @Size(min = 2, max = 2, message = "El código de país debe tener exactamente 2 caracteres")
  private String countryCode;

  // Código ISO de la moneda — PEN, USD, COP, MXN, etc
  @NotBlank(message = "La moneda es obligatoria")
  @Size(min = 3, max = 3, message = "El código de moneda debe tener exactamente 3 caracteres")
  private String currencyCode;

  // ID del plan elegido — Starter, Business o Enterprise
  @NotNull(message = "El plan es obligatorio")
  private UUID planId;

// ─── DATOS DEL ADMINISTRADOR ──────────────────────────────────

  // Email del administrador — será su usuario de login
  @NotBlank(message = "El email del administrador es obligatorio")
  @Email(message = "El email no tiene un formato válido")
  @Size(max = 255, message = "El email no puede superar 255 caracteres")
  private String adminEmail;

  // Nombre completo del administrador
  @NotBlank(message = "El nombre del administrador es obligatorio")
  @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
  private String adminName;

  // Teléfono — opcional
  @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
  private String adminPhone;

  // ─── DATOS OPCIONALES ─────────────────────────────────────────

  // Datos fiscales — varía por país
  // PE: {"type": "RUC", "number": "20123456789"}
  // CO: {"type": "NIT", "number": "900123456-7"}
  // Opcional al registrarse — puede completarse después
  private Map<String, Object> taxInfo;
}
