package com.miroapp.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// CreateUserResponse — respuesta al crear un nuevo empleado
//
// Retorna los datos del usuario creado
// más la contraseña temporal que debe cambiar al primer login
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserResponse {

  // Datos del usuario creado
  private UserResponse user;

  // Contraseña temporal — mostrar UNA SOLA VEZ
  // El admin se la comunica al empleado
  private String temporaryPassword;

  // Instrucciones para el admin
  private String instructionMessage;

}
