package com.miroapp.tenant.exception;

// =====================================================================
// TenantNotFoundException
//
// Se lanza cuando buscas un tenant que no existe
// o que fue eliminado (soft delete).
//
// El GlobalExceptionHandler la captura y retorna 404 Not Found
// =====================================================================
public class TenantNotFoundException extends RuntimeException{
  public TenantNotFoundException(String message) {
    super(message);
  }
}
