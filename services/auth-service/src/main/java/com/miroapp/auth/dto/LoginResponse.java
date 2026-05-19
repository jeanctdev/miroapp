package com.miroapp.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// LoginResponse — datos que retorna el endpoint de login
//
// El sistema retorna:
// {
//   "token": "eyJhbGci...",
//   "mustChangePassword": true,
//   "user": { ... }
// }
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
  // JWT generado — el cliente lo guarda y lo envía en cada request
  private String token;

  // Si true → el frontend redirige al cambio de contraseña
  // Si false → el usuario puede operar normalmente
  private boolean mustChangePassword;

  // Datos básicos del usuario para mostrar en la UI
  private UserResponse user;
}
