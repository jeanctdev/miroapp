package com.miroapp.common.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// =====================================================================
// ApiResponse<T> — respuesta estándar de todos los microservicios
//
// ¿Por qué genérico <T>?
// → T puede ser cualquier tipo: TenantResponse, UserResponse, etc
// → El formato del sobre siempre es igual
// → El contenido de "data" varía según el endpoint
//
// @JsonInclude(NON_NULL) → los campos null no aparecen en el JSON
// → Si success=true  → "error" no aparece en el JSON
// → Si success=false → "data"  no aparece en el JSON
// → JSON más limpio y liviano
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  // ¿La operación fue exitosa?
  // true  → operación completada sin errores
  // false → ocurrió algún error
  private boolean success;

  // Los datos retornados — solo cuando success = true
  // NULL cuando success = false
  private T data;

  // El error ocurrido — solo cuando success = false
  // NULL cuando success = true
  private ApiError error;

  // Fecha y hora exacta de la respuesta en UTC-5 (Lima)
  // Útil para debugging y logs del frontend
  @Builder.Default
  private OffsetDateTime timestamp = OffsetDateTime.now();

  // El endpoint que generó esta respuesta
  // Útil para debugging
  private String path;

  // El código HTTP de la respuesta
  // 200, 201, 400, 404, 409, 500
  private int httpStatus;

  // ─── MÉTODOS ESTÁTICOS DE FÁBRICA ─────────────────────────────
  // Facilitan la creación de respuestas desde el Controller
  // En lugar de usar el Builder manualmente cada vez

  // Respuesta exitosa con datos — para 200 OK y 201 Created
  // Uso: ApiResponse.ok(tenantResponse, "/api/tenants/register", 201)
  public static <T> ApiResponse<T> ok(T data, String path, int httpStatus) {
    return ApiResponse.<T>builder()
      .success(true)
      .data(data)
      .path(path)
      .httpStatus(httpStatus)
      .build();
  }

  // Respuesta de error — para 400, 404, 409, 500, etc
  // Uso: ApiResponse.error("RESOURCE_NOT_FOUND",
  //                        "No existe el tenant",
  //                        null, "/api/tenants/venedog", 404)
  public static <T> ApiResponse<T> error(
    String code,
    String message,
    String field,
    String path,
    int httpStatus) {
    return ApiResponse.<T>builder()
      .success(false)
      .error(ApiError.builder()
        .code(code)
        .message(message)
        .field(field)
        .build())
      .path(path)
      .httpStatus(httpStatus)
      .build();
  }


}
