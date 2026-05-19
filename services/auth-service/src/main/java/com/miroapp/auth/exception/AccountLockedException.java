package com.miroapp.auth.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

// =====================================================================
// AccountLockedException — cuenta bloqueada por intentos fallidos
//
// Se lanza cuando el usuario supera 5 intentos fallidos
// El GlobalExceptionHandler retorna 401 Unauthorized
// =====================================================================
public class AccountLockedException extends BusinessException {

  public AccountLockedException(String message) {
    super(
      ErrorCodes.ACCOUNT_LOCKED,
      message
    );
  }
}