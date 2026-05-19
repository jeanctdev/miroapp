package com.miroapp.auth.dto;

import com.miroapp.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// =====================================================================
// CreateUserRequest — datos para crear un nuevo empleado
//
// Solo el TENANT_ADMIN puede crear usuarios
// El sistema genera una contraseña temporal automáticamente
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

  @NotBlank(message = "{auth.email.required}")
  @Email(message = "{auth.email.invalid}")
  private String email;

  @NotBlank(message = "{auth.first.name.required}")
  @Size(max = 100, message = "{auth.first.name.max}")
  private String firstName;

  @NotBlank(message = "{auth.last.name.required}")
  @Size(max = 100, message = "{auth.last.name.max}")
  private String lastName;

  @Size(max = 20, message = "{auth.phone.max}")
  private String phone;

  @NotNull(message = "{auth.role.required}")
  private Role role;

  // Obligatorio para MANAGER, CASHIER, VIEWER
  // NULL para TENANT_ADMIN
  private UUID branchId;
}
