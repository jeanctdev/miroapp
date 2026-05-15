package com.miroapp.common.exception;

import com.miroapp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// =====================================================================
// GlobalExceptionHandler — captura todas las excepciones
//
// @RestControllerAdvice → intercepta excepciones de todos los
// @RestController del microservicio automáticamente
//
// Spring busca el @ExceptionHandler más específico primero:
// → BusinessException      → handler específico
// → ResourceNotFoundException → handler específico
// → Exception              → handler genérico (último recurso)
// =====================================================================
@RestControllerAdvice
public class GlobalExceptionHandler {

  // ─── ERRORES DE VALIDACIÓN ────────────────────────────────────
  // Se lanza cuando @Valid falla en el Controller
  // Ejemplo: email inválido, slug vacío, planId null
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
    MethodArgumentNotValidException ex,
    HttpServletRequest request) {

    // Tomamos el primer error de validación
    // En el futuro podemos retornar todos los errores
    FieldError fieldError = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .findFirst()
      .orElse(null);

    String field = fieldError != null ? fieldError.getField() : null;
    String message = fieldError != null
      ? fieldError.getDefaultMessage()
      : "Error de validación";

    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(ApiResponse.error(
        ErrorCodes.VALIDATION_ERROR,
        message,
        field,
        request.getRequestURI(),
        HttpStatus.BAD_REQUEST.value()
      ));
  }

  // ─── ERRORES DE NEGOCIO ───────────────────────────────────────
  // Se lanza desde el Service cuando una regla de negocio falla
  // Ejemplo: slug duplicado, límite de plan alcanzado
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request) {

    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(ApiResponse.error(
        ex.getCode(),
        ex.getMessage(),
        ex.getField(),
        request.getRequestURI(),
        HttpStatus.BAD_REQUEST.value()
      ));
  }

  // ─── RECURSO NO ENCONTRADO ────────────────────────────────────
  // Se lanza cuando buscas algo que no existe en la BD
  // Ejemplo: GET /api/tenants/venedog → no existe
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
    ResourceNotFoundException ex,
    HttpServletRequest request) {

    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(ApiResponse.error(
        ErrorCodes.RESOURCE_NOT_FOUND,
        ex.getMessage(),
        null,
        request.getRequestURI(),
        HttpStatus.NOT_FOUND.value()
      ));
  }

  // ─── ERROR INTERNO ────────────────────────────────────────────
  // Captura cualquier excepción no manejada
  // Es el último recurso — nunca debería llegar aquí
  // Si llega → hay un bug que hay que corregir
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(
    Exception ex,
    HttpServletRequest request) {

    // Log del error real — solo visible en el servidor
    // El cliente nunca ve el detalle técnico
    ex.printStackTrace();

    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResponse.error(
        ErrorCodes.INTERNAL_SERVER_ERROR,
        "Ocurrió un error inesperado. Por favor intenta de nuevo.",
        null,
        request.getRequestURI(),
        HttpStatus.INTERNAL_SERVER_ERROR.value()
      ));
  }

}
