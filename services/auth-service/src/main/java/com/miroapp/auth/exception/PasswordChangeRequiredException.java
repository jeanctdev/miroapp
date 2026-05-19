package com.miroapp.auth.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

// =====================================================================
// PasswordChangeRequiredException
//
// Se lanza cuando el usuario intenta acceder a un endpoint
// protegido sin haber cambiado su contraseña temporal
// El GlobalExceptionHandler retorna 403 Forbidden
// =====================================================================
public class PasswordChangeRequiredException extends BusinessException {
  public PasswordChangeRequiredException(String message) {
    super(
      ErrorCodes.PASSWORD_CHANGE_REQUIRED,
      message
    );
  }
}
