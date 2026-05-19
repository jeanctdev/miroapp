package com.miroapp.common.exception;

// =====================================================================
// ErrorCodes — constantes de códigos de error de Miro
//
// ¿Por qué una clase de constantes?
// → Evita escribir Strings a mano en todo el código
// → Un solo lugar para definir y cambiar los códigos
// → IntelliJ autocompleta — sin errores de tipeo
//
// Uso:
// throw new BusinessException(
//     ErrorCodes.TENANT_ALREADY_EXISTS,
//     "El slug 'venedog' ya está registrado",
//     "slug"
// );
// =====================================================================
public class ErrorCodes {

  // Constructor privado → no se puede instanciar
  // Es una clase de utilidad — solo tiene constantes
  private ErrorCodes() {}
  // ─── ERRORES DE VALIDACIÓN ────────────────────────────────────
  // Campos inválidos en el request
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  // ─── ERRORES DE NEGOCIO ───────────────────────────────────────
  // Reglas del negocio violadas
  public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
  // ─── ERRORES DE AUTENTICACIÓN ─────────────────────────────────
  public static final String UNAUTHORIZED              = "UNAUTHORIZED";
  public static final String FORBIDDEN                 = "FORBIDDEN";
  public static final String INVALID_CREDENTIALS       = "INVALID_CREDENTIALS";
  public static final String ACCOUNT_LOCKED            = "ACCOUNT_LOCKED";
  public static final String PASSWORD_CHANGE_REQUIRED  = "PASSWORD_CHANGE_REQUIRED";
  // ─── ERRORES DE RECURSOS ──────────────────────────────────────
  public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
  // ─── ERRORES DE CONFLICTO ─────────────────────────────────────
  // Duplicados en la BD
  public static final String TENANT_ALREADY_EXISTS = "TENANT_ALREADY_EXISTS";
  public static final String USER_ALREADY_EXISTS   = "USER_ALREADY_EXISTS";
  // ─── ERRORES INTERNOS ─────────────────────────────────────────
  public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
  // ─── ERRORES DE INFRAESTRUCTURA ───────────────────────────────
  public static final String SCHEMA_CREATION_FAILED = "SCHEMA_CREATION_FAILED";
  public static final String MIGRATION_FAILED       = "MIGRATION_FAILED";
  public static final String USER_CREATION_FAILED   = "USER_CREATION_FAILED";

}
