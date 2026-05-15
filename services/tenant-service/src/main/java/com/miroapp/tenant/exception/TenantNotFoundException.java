package com.miroapp.tenant.exception;

import com.miroapp.common.exception.ResourceNotFoundException;

// =====================================================================
// TenantNotFoundException
//
// Se lanza cuando buscas un tenant que no existe
// o que fue eliminado con soft delete.
//
// Extiende ResourceNotFoundException de common-lib
// → GlobalExceptionHandler la captura automáticamente
// → Retorna HTTP 404 Not Found
// =====================================================================
public class TenantNotFoundException extends ResourceNotFoundException {

  public TenantNotFoundException(String message) {
    super(message);
  }

}
