package com.miroapp.tenant.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

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
  public TenantAlreadyExistsException(String message, String field) {
    super(
      ErrorCodes.TENANT_ALREADY_EXISTS,
      message,
      field
    );
  }
}
