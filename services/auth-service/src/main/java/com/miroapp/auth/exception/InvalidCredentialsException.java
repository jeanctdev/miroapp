package com.miroapp.auth.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

// =====================================================================
// InvalidCredentialsException — credenciales incorrectas
//
// Se lanza cuando el email o contraseña son incorrectos
// El GlobalExceptionHandler retorna 401 Unauthorized
// =====================================================================
public class InvalidCredentialsException extends BusinessException {
  public InvalidCredentialsException(String message) {
    super(
      ErrorCodes.INVALID_CREDENTIALS,
      message
    );
  }
}
