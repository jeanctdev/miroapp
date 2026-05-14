package com.miroapp.tenant.dto;

import com.miroapp.tenant.entity.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

// =====================================================================
// TenantResponse — datos que retorna el endpoint al cliente
//
// ¿Qué NO incluimos aquí?
// → deleted_at → campo interno, el cliente no necesita saberlo
// → settings   → configuraciones internas del sistema
//
// ¿Qué SÍ incluimos?
// → Todo lo que el cliente necesita ver sobre su tenant
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

  private UUID id;
  private String slug;
  private String name;
  private String countryCode;
  private String currencyCode;
  private UUID planId;
  private String adminEmail;
  private String adminName;
  private String adminPhone;
  private Map<String, Object> taxInfo;
  private TenantStatus status;
  private OffsetDateTime trialEndsAt;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  // Mensaje informativo sobre el estado del trial
  // "Tienes 10 días de prueba gratuita restantes"
  private String trialMessage;
}
