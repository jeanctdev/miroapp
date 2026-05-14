package com.miroapp.tenant.exception;

// =====================================================================
// TenantAlreadyExistsException
//
// Se lanza cuando intentas registrar un tenant con un slug
// o email que ya existe en el sistema.
//
// Extiende RuntimeException → no necesita ser declarada
// en la firma del método (unchecked exception)
// El GlobalExceptionHandler la captura y retorna 409 Conflict
// =====================================================================
public class TenantAlreadyExistsException extends RuntimeException{

  public TenantAlreadyExistsException(String message) {
    super(message);
  }
}
