package com.miroapp.common.exception;

// =====================================================================
// ResourceNotFoundException — 404 Not Found
//
// Se lanza cuando buscas algo que no existe en la BD.
//
// Ejemplos de uso:
// → GET /api/tenants/venedog → no existe → lanzar esta excepción
// → GET /api/products/uuid   → no existe → lanzar esta excepción
//
// El GlobalExceptionHandler la captura y retorna:
// HTTP 404 + code: "RESOURCE_NOT_FOUND"
// =====================================================================
public class ResourceNotFoundException extends RuntimeException{

  // resource → qué tipo de recurso no se encontró ("Tenant", "Product")
  // identifier → con qué valor se buscó ("venedog", "uuid-123")
  public ResourceNotFoundException(String resource, String identifier) {
    super(String.format("No existe %s con el identificador '%s'",
      resource, identifier));
  }

  // Constructor simple con mensaje directo
  public ResourceNotFoundException(String message) {
    super(message);
  }

}
