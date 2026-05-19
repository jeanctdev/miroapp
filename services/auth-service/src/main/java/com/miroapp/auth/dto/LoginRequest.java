package com.miroapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// LoginRequest — datos que recibe el endpoint de login
//
// El cliente envía:
// {
//   "email": "admin@venedog.com",
//   "password": "mi_password",
//   "tenantSlug": "venedog"
// }
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

  @NotBlank(message = "{auth.email.required}")
  @Email(message = "{auth.email.invalid}")
  private String email;

  @NotBlank(message = "{auth.password.required}")
  private String password;

  @NotBlank(message = "{auth.tenant.required}")
  private String tenantSlug;
}
