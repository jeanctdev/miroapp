package com.miroapp.common.exception;

import org.springframework.http.HttpStatus;

// =====================================================================
// BusinessException — 400 Bad Request (regla de negocio)
//
// Se lanza cuando una regla de negocio no se cumple.
// No es un error técnico — es una restricción del negocio.
//
// Ejemplos de uso:
// → Crear sucursal superando el límite del plan
// → Agregar usuario superando max_users del plan
// → Intentar vender con stock insuficiente
// → Intentar registrar un slug ya existente
//
// El GlobalExceptionHandler la captura y retorna:
// HTTP 400 + code: definido al lanzar la excepción
// =====================================================================
public class BusinessException extends RuntimeException{

  // Código del error — se usa en ApiError.code
  private final String code;

  // field → campo específico que causó el error (nullable)
  private final String field;

  // Constructor con código y mensaje — para errores generales
  public BusinessException(String code, String message) {
    super(message);
    this.code = code;
    this.field = null;
  }

  // Constructor con código, mensaje y campo — para errores de campo
  public BusinessException(String code, String message, String field) {
    super(message);
    this.code = code;
    this.field = field;
  }

  public String getCode() {
    return code;
  }

  public String getField() {
    return field;
  }
}
