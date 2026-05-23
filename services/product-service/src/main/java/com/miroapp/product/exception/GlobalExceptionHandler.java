package com.miroapp.product.exception;

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
// GlobalExceptionHandler — manejo de errores del product-service
// =====================================================================
//      product-service → ProductNotFoundException,
//                         CategoryNotFoundException,
//                         DuplicateSkuException
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
    MethodArgumentNotValidException ex,
    HttpServletRequest request) {

    FieldError fieldError = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .findFirst()
      .orElse(null);

    String field   = fieldError != null ? fieldError.getField() : null;
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

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(
    BusinessException ex,
    HttpServletRequest request) {

    HttpStatus status = switch (ex.getCode()) {
      case ErrorCodes.RESOURCE_NOT_FOUND  -> HttpStatus.NOT_FOUND;
      case ErrorCodes.UNAUTHORIZED,
           ErrorCodes.INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.FORBIDDEN           -> HttpStatus.FORBIDDEN;
      default                             -> HttpStatus.BAD_REQUEST;
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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(
    Exception ex,
    HttpServletRequest request) {

    log.error("Error interno no controlado en [{}]: {}",
      request.getRequestURI(), ex.getMessage(), ex);

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
