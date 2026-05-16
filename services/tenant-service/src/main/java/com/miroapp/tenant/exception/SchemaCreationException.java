package com.miroapp.tenant.exception;

import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;

// =====================================================================
// SchemaCreationException
//
// Se lanza cuando falla la creación del schema de un tenant
// o la ejecución de las migraciones Flyway.
//
// Extiende BusinessException → GlobalExceptionHandler
// la captura y retorna 500 Internal Server Error
// porque es un error de infraestructura, no del cliente
// =====================================================================
public class SchemaCreationException extends BusinessException {

  // Constructor para fallo al crear el schema
  public SchemaCreationException(String slug, Throwable cause) {
    super(
      ErrorCodes.SCHEMA_CREATION_FAILED,
      String.format("No se pudo crear el schema para el tenant '%s'", slug)
    );
    initCause(cause);
  }

  // Constructor para fallo en migraciones
  public SchemaCreationException(String slug, String operation, Throwable cause) {
    super(
      ErrorCodes.MIGRATION_FAILED,
      String.format("Error en %s para el tenant '%s'", operation, slug)
    );
    initCause(cause);
  }
}
