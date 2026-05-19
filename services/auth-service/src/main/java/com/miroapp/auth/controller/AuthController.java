package com.miroapp.auth.controller;

import com.miroapp.auth.dto.*;
import com.miroapp.auth.service.AuthService;
import com.miroapp.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// =====================================================================
// AuthController — endpoints REST de autenticación
//
// ¿Qué es @RestController?
// → Combina @Controller + @ResponseBody
// → Todos los métodos retornan JSON automáticamente
// → Sin esta anotación → Spring no registra los endpoints
//
// ¿Qué es @RequestMapping("/api/auth")?
// → Prefijo de URL para todos los endpoints de este Controller
// → POST /api/auth/login
// → GET  /api/auth/me
// → PUT  /api/auth/change-password
// → POST /api/auth/users
//
// ¿Qué es Authentication?
// → Spring Security lo inyecta automáticamente
// → Contiene los datos del usuario autenticado
// → Los pone JwtAuthenticationFilter en el SecurityContext
// → principal → userId
// → details   → tenantSlug
// → authorities → [ROLE_TENANT_ADMIN]
//
// RUTAS PÚBLICAS (sin JWT):
// → POST /api/auth/login
//
// RUTAS PRIVADAS (con JWT):
// → GET  /api/auth/me
// → PUT  /api/auth/change-password
// → POST /api/auth/users → solo TENANT_ADMIN
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final MessageSource messageSource;

  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code, args, code, LocaleContextHolder.getLocale());
  }

  // ─── POST /api/auth/login ─────────────────────────────────────
  // PÚBLICO — sin JWT
  // El cliente envía email, password y tenantSlug
  // El sistema retorna JWT + datos del usuario
  //
  // @PostMapping("/login") → solo acepta HTTP POST
  // @Valid → activa las validaciones del DTO
  //          si falla → GlobalExceptionHandler retorna 400
  // @RequestBody → el body del request se convierte al DTO
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
    @Valid @RequestBody LoginRequest request) {

    log.info("Request de login: {} en tenant: {}", request.getEmail(), request.getTenantSlug());

    LoginResponse response = authService.login(request);

    return ResponseEntity
      .status(HttpStatus.OK)
      .body(ApiResponse.ok(
        response, "/api/auth/login", HttpStatus.OK.value()
      ));
  }

  // ─── GET /api/auth/me ─────────────────────────────────────────
  // PRIVADO — requiere JWT válido
  // Retorna los datos del usuario autenticado
  //
  // Authentication → Spring Security lo inyecta automáticamente
  //                  JwtAuthenticationFilter lo llenó antes
  //
  // ¿Cómo obtiene Spring el Authentication?
  // → JwtAuthenticationFilter ejecutó:
  //   SecurityContextHolder.getContext().setAuthentication(auth)
  // → Spring lo inyecta automáticamente en el parámetro
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getMe(
    Authentication authentication) {

    // principal → userId guardado por JwtAuthenticationFilter
    String userId = (String) authentication.getPrincipal();

    // details → tenantSlug guardado por JwtAuthenticationFilter
    String tenantSlug = (String) authentication.getDetails();

    log.debug("GetMe para userId: {} en tenant: {}",
      userId, tenantSlug);

    UserResponse response = authService.getMe(userId, tenantSlug);

    return ResponseEntity
      .status(HttpStatus.OK)
      .body(ApiResponse.ok(
        response,
        "/api/auth/me",
        HttpStatus.OK.value()
      ));
  }

  // ─── PUT /api/auth/change-password ────────────────────────────
  // PRIVADO — requiere JWT válido
  // Cualquier rol puede cambiar su propia contraseña
  // Es OBLIGATORIO si mustChangePassword = true en el JWT
  //
  // ¿Qué valida?
  // → currentPassword → debe coincidir con el hash en BD
  // → newPassword → mínimo 8 chars, 1 mayúscula, 1 número, 1 símbolo
  // → confirmPassword → debe coincidir con newPassword
  // → newPassword != currentPassword → no puede ser igual
  @PutMapping("/change-password")
  public ResponseEntity<ApiResponse<String>> changePassword(
    @Valid @RequestBody ChangePasswordRequest request,
    Authentication authentication) {

    String userId     = (String) authentication.getPrincipal();
    String tenantSlug = (String) authentication.getDetails();

    log.info("Cambio de contraseña para userId: {}", userId);

    authService.changePassword(userId, tenantSlug, request);

    return ResponseEntity
      .status(HttpStatus.OK)
      .body(ApiResponse.ok(
        getMessage("auth.password.changed"),
        "/api/auth/change-password",
        HttpStatus.OK.value()
      ));
  }

  // ─── POST /api/auth/users ─────────────────────────────────────
  // PRIVADO — solo TENANT_ADMIN
  // Crea un nuevo empleado en el tenant
  //
  // @PreAuthorize("hasRole('TENANT_ADMIN')")
  // → Spring Security verifica el rol ANTES de ejecutar el método
  // → Si el rol no es TENANT_ADMIN → 403 Forbidden automático
  // → Sin llegar al Service ni al Repository
  //
  // ¿Por qué solo TENANT_ADMIN puede crear usuarios?
  // → Es el dueño del negocio
  // → Él decide quién tiene acceso al sistema
  // → MANAGER, CASHIER, VIEWER no pueden crear usuarios
  @PostMapping("/users")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<CreateUserResponse>> createUser(
    @Valid @RequestBody CreateUserRequest request,
    Authentication authentication) {

    // userId del TENANT_ADMIN que está creando el usuario
    // Se guarda en created_by del nuevo usuario
    String createdByUserId = (String) authentication.getPrincipal();
    String tenantSlug      = (String) authentication.getDetails();

    log.info("TENANT_ADMIN {} creando usuario {} en tenant: {}",
      createdByUserId, request.getEmail(), tenantSlug);

    CreateUserResponse response = authService.createUser(
      tenantSlug,
      createdByUserId,
      request
    );

    // 201 Created → se creó un nuevo recurso
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.ok(
        response,
        "/api/auth/users",
        HttpStatus.CREATED.value()
      ));
  }



}
