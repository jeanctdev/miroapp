package com.miroapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// ChangePasswordRequest — datos para cambiar la contraseña
//
// Reglas de contraseña fuerte:
// → Mínimo 8 caracteres
// → Al menos 1 mayúscula
// → Al menos 1 número
// → Al menos 1 símbolo especial
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
  @NotBlank(message = "{auth.current.password.required}")
  private String currentPassword;

  @NotBlank(message = "{auth.new.password.required}")
  @Size(min = 8, message = "{auth.password.min.length}")
  @Pattern(
    regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!]).*$",
    message = "{auth.password.pattern}"
  )
  private String newPassword;

  @NotBlank(message = "{auth.confirm.password.required}")
  private String confirmPassword;
}
