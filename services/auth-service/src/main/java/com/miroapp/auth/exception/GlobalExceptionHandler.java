package com.miroapp.auth.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// =====================================================================
// GlobalExceptionHandler — manejo centralizado de errores del auth-service
// =====================================================================
// ¿Por qué está aquí y no en common-lib?
//
// @RestControllerAdvice pertenece a spring-webmvc (MVC).
// El gateway usa WebFlux — no puede cargar esta clase.
// Además, cada servicio tiene sus propias excepciones de dominio:
//   auth-service    → InvalidCredentialsException, AccountLockedException
//   tenant-service  → TenantAlreadyExistsException, SchemaCreationException
//   product-service → ProductNotFoundException, DuplicateSkuException
//
// No tiene sentido centralizar excepciones específicas de cada dominio.
// Lo que sí está centralizado en common-lib es ApiResponse<T> y ErrorCodes
// — el formato y los códigos son iguales en todos los servicios.
//
// @RestControllerAdvice → intercepta excepciones de todos los
// @RestController del auth-service automáticamente.
// =====================================================================
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code, args, code, LocaleContextHolder.getLocale()
    );
  }

  // ── Errores de validación (@Valid en el Controller) ───────────
  // Se lanza cuando un campo del request no pasa las validaciones
  // @NotBlank, @Email, @Size, @Pattern, etc.
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
    MethodArgumentNotValidException ex,
    HttpServletRequest request) {

    FieldError fieldError = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .findFirst()
      .orElse(null);

    String field = fieldError != null ? fieldError.getField() : null;
    String message = fieldError != null
      ? fieldError.getDefaultMessage()
      : getMessage("error.validation");

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

  // ── Errores de negocio (BusinessException) ────────────────────
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request) {

    HttpStatus status = switch (ex.getCode()) {
      case ErrorCodes.TENANT_ALREADY_EXISTS,
           ErrorCodes.USER_ALREADY_EXISTS     -> HttpStatus.CONFLICT;
      case ErrorCodes.UNAUTHORIZED,
           ErrorCodes.INVALID_CREDENTIALS,
           ErrorCodes.ACCOUNT_LOCKED          -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.FORBIDDEN,
           ErrorCodes.PASSWORD_CHANGE_REQUIRED -> HttpStatus.FORBIDDEN;
      case ErrorCodes.SCHEMA_CREATION_FAILED,
           ErrorCodes.MIGRATION_FAILED,
           ErrorCodes.USER_CREATION_FAILED    -> HttpStatus.INTERNAL_SERVER_ERROR;
      default                                 -> HttpStatus.BAD_REQUEST;
    };

    return ResponseEntity
      .status(status)
      .body(ApiResponse.error(
        ex.getCode(),
        ex.getMessage(),
        ex.getField(),
        request.getRequestURI(),
        status.value()
      ));
  }

  // ── Recurso no encontrado (ResourceNotFoundException) ─────────
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

  // ── Error interno — último recurso ────────────────────────────
  // Captura cualquier excepción no manejada.
  // Si llega aquí → hay un bug que corregir.
  // El cliente nunca ve el detalle técnico.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(
    Exception ex,
    HttpServletRequest request) {

    log.error("Error interno en [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);

    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResponse.error(
        ErrorCodes.INTERNAL_SERVER_ERROR,
        getMessage("error.internal"),
        null,
        request.getRequestURI(),
        HttpStatus.INTERNAL_SERVER_ERROR.value()
      ));
  }
}
