package com.miroapp.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// ApiError — estructura del error dentro de ApiResponse
//
// Cuando success = false, este objeto va dentro de ApiResponse.error
//
// Ejemplo en JSON:
// {
//   "success": false,
//   "error": {
//     "code": "TENANT_ALREADY_EXISTS",
//     "message": "El slug 'venedog' ya está registrado",
//     "field": "slug"
//   }
// }
//
// code    → identificador único del error
//           el frontend toma decisiones basado en este código
//           Ejemplos: VALIDATION_ERROR, RESOURCE_NOT_FOUND
//
// message → mensaje legible en español para mostrar al usuario
//           nunca expone detalles técnicos internos
//
// field   → campo específico que causó el error
//           solo en errores de validación
//           NULL en errores generales como 404 o 500
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

  // Código único del error — el frontend toma decisiones con esto
  private String code;

  // Mensaje legible en español para el usuario
  private String message;

  // Campo que causó el error — nullable
  // "slug", "adminEmail", "planId", etc
  private String field;

}
