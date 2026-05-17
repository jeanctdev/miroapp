package com.miroapp.tenant.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

// =====================================================================
// UserCreationException
//
// Se lanza cuando falla la creación del usuario TENANT_ADMIN
// al registrar una nueva empresa.
//
// Extiende BusinessException → GlobalExceptionHandler
// la captura y retorna 500 Internal Server Error
// porque es un error de infraestructura, no del cliente
// =====================================================================
public class UserCreationException extends BusinessException {
  public UserCreationException(String tenantSlug, Throwable cause) {
    super(
      ErrorCodes.USER_CREATION_FAILED,
      String.format(
        "No se pudo crear el usuario administrador para '%s'",
        tenantSlug
      )
    );
    initCause(cause);
  }
}
