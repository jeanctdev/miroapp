package com.miroapp.tenant.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

// =====================================================================
// TenantAlreadyExistsException
//
// Se lanza cuando intentas registrar un tenant con un slug
// o email que ya existe en el sistema.
//
// Extiende BusinessException de common-lib
// → GlobalExceptionHandler la captura automáticamente
// → Retorna HTTP 409 Conflict
// =====================================================================
public class TenantAlreadyExistsException extends BusinessException {

  // Constructor para slug duplicado
  public TenantAlreadyExistsException(String field, String value) {
    super(
      ErrorCodes.TENANT_ALREADY_EXISTS,
      String.format("El %s '%s' ya está registrado en el sistema", field, value),
      field
    );
  }
}
