package com.miroapp.auth.service;

import com.miroapp.auth.dto.*;
import com.miroapp.auth.entity.Role;
import com.miroapp.auth.entity.User;
import com.miroapp.auth.exception.AccountLockedException;
import com.miroapp.auth.exception.InvalidCredentialsException;
import com.miroapp.auth.exception.UserNotFoundException;
import com.miroapp.auth.repository.UserRepository;
import com.miroapp.common.exception.BusinessException;
import com.miroapp.common.exception.ErrorCodes;
import com.miroapp.common.exception.ResourceNotFoundException;
import com.miroapp.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// =====================================================================
// AuthService — lógica de negocio de autenticación
//
// Responsabilidades:
// → login()          → autenticar usuario y generar JWT
// → changePassword() → cambiar contraseña con validaciones
// → getMe()          → obtener datos del usuario actual
// → createUser()     → crear nuevo empleado (solo TENANT_ADMIN)
//
// Multi-tenancy:
// → Cada operación recibe el tenantSlug
// → Ejecuta SET search_path TO "{tenantSlug}"
// → Todas las queries van al schema correcto
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final MessageSource messageSource;
  private final JdbcTemplate jdbcTemplate;

  // Máximo de intentos fallidos antes del bloqueo
  private static final int MAX_FAILED_ATTEMPTS = 5;

  // Minutos de bloqueo al superar los intentos
  private static final int LOCK_DURATION_MINUTES = 15;

  // Caracteres para contraseña temporal de nuevos usuarios
  private static final String CHARS =
    "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";

  // ─── HELPER ───────────────────────────────────────────────────
  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code, args, code, LocaleContextHolder.getLocale());
  }

  // ─── SET SEARCH PATH ──────────────────────────────────────────
  // Cambia el schema activo para todas las queries de este hilo
  // Debe llamarse antes de cualquier operación en {tenant}.users
  private void setSearchPath(String tenantSlug) {
    jdbcTemplate.execute(
      String.format("SET search_path TO \"%s\"", tenantSlug));
    log.debug("Search path cambiado a: {}", tenantSlug);
  }

  // ─── LOGIN ────────────────────────────────────────────────────
  @Transactional(noRollbackFor = {
    InvalidCredentialsException.class,
    AccountLockedException.class
  })
  public LoginResponse login(LoginRequest request){
    log.info("Intento de login: {} en tenant: {}",
      request.getEmail(), request.getTenantSlug());

    // PASO 1 — Cambiar al schema del tenant
    setSearchPath(request.getTenantSlug());

    // PASO 2 — Buscar el usuario
    // Si no existe → credenciales inválidas
    // No decimos "usuario no encontrado" por seguridad
    // Para no revelar qué emails están registrados
    User user = userRepository
      .findByEmailAndDeletedAtIsNull(request.getEmail())
      .orElseThrow(() -> {
        log.warn("Usuario no encontrado: {}",
          request.getEmail());
        return new InvalidCredentialsException(
          getMessage("auth.invalid.credentials"));
      });

    // PASO 3 — Verificar cuenta activa
    if (!user.isActive()) {
      log.warn("Login en cuenta inactiva: {}", request.getEmail());
      throw new InvalidCredentialsException(
        getMessage("auth.user.inactive"));
    }

    // PASO 4 — Verificar bloqueo
    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
      long minutesLeft = ChronoUnit.MINUTES.between(
        OffsetDateTime.now(), user.getLockedUntil());

      log.warn("Login en cuenta bloqueada: {}", request.getEmail());
      throw new AccountLockedException(
        getMessage("auth.account.locked", minutesLeft));
    }

    // PASO 5 — Verificar contraseña
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      log.warn("Contraseña incorrecta: {}", request.getEmail());
      handleFailedLogin(user);
      throw new InvalidCredentialsException(getMessage("auth.invalid.credentials"));
    }

    // PASO 6 — Login exitoso → resetear intentos fallidos
    userRepository.resetFailedAttemptsAndUpdateLogin(
      user.getId(), OffsetDateTime.now());

    log.info("Login exitoso: {} en tenant: {}", request.getEmail(), request.getTenantSlug());

    // PASO 7 — Generar JWT
    String token = jwtUtil.generateToken(
      user.getId(),
      request.getTenantSlug(),
      user.getRole().name(),
      user.getBranchId(),
      user.getEmail(),
      user.isMustChangePassword()
    );

    return LoginResponse.builder()
      .token(token)
      .mustChangePassword(user.isMustChangePassword())
      .user(toUserResponse(user))
      .build();

  }

  // ─── CAMBIAR CONTRASEÑA ───────────────────────────────────────
  @Transactional
  public void changePassword(
    String userId,
    String tenantSlug,
    ChangePasswordRequest request) {

    log.info("Cambio de contraseña para userId: {}", userId);

    // PASO 1 — Cambiar al schema del tenant
    setSearchPath(tenantSlug);

    // PASO 2 — Buscar el usuario
    User user = userRepository
      .findById(UUID.fromString(userId))
      .orElseThrow(() -> new UserNotFoundException(
        getMessage("auth.user.not.found", userId)));

    // PASO 3 — Verificar contraseña actual
    if (!passwordEncoder.matches(
      request.getCurrentPassword(), user.getPasswordHash())) {
      throw new InvalidCredentialsException(getMessage("auth.current.password.invalid"));
    }

    // PASO 4 — Verificar que la nueva contraseña
    //          es diferente a la actual
    if (passwordEncoder.matches(
      request.getNewPassword(), user.getPasswordHash())) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR, getMessage("auth.password.same"), "newPassword");
    }

    // PASO 5 — Verificar que las contraseñas coinciden
    if (!request.getNewPassword().equals(request.getConfirmPassword())) {
      throw new BusinessException(ErrorCodes.VALIDATION_ERROR, getMessage("auth.password.mismatch"), "confirmPassword");
    }

    // PASO 6 — Hashear y guardar la nueva contraseña
    String hashedPassword = passwordEncoder.encode(request.getNewPassword());
    userRepository.updatePassword(user.getId(), hashedPassword, OffsetDateTime.now());
    log.info("Contraseña cambiada exitosamente para userId: {}", userId);
  }

  // ─── GET ME ───────────────────────────────────────────────────
  @Transactional(readOnly = true)
  public UserResponse getMe(String userId, String tenantSlug) {
    log.debug("GetMe para userId: {} en tenant: {}", userId, tenantSlug);
    setSearchPath(tenantSlug);
    User user = userRepository
      .findById(UUID.fromString(userId))
      .orElseThrow(() -> new ResourceNotFoundException(
        "Usuario", userId));

    return toUserResponse(user);
  }

  // ─── CREAR USUARIO ────────────────────────────────────────────
  @Transactional
  public CreateUserResponse createUser(String tenantSlug,
                                       String createdByUserId,
                                       CreateUserRequest request) {
    log.info("Creando usuario {} en tenant: {}",
      request.getEmail(), tenantSlug);

    setSearchPath(tenantSlug);

    // PASO 1 — Verificar que el email no existe
    if (userRepository.existsByEmailAndDeletedAtIsNull(
      request.getEmail())) {
      throw new BusinessException(ErrorCodes.USER_ALREADY_EXISTS,
        getMessage("auth.user.already.exists",
          request.getEmail()), "email");
    }

    // PASO 2 — Validar branchId para roles que lo requieren
    if (request.getRole() != Role.TENANT_ADMIN && request.getBranchId() == null) {
      throw new BusinessException(
        ErrorCodes.VALIDATION_ERROR,
        getMessage("auth.branch.required.for.role",
          request.getRole().name()),
        "branchId"
      );
    }

    // PASO 3 — Generar contraseña temporal
    String temporaryPassword = generateTemporaryPassword();
    String hashedPassword = passwordEncoder.encode(temporaryPassword);

    // PASO 4 — Crear el usuario
    User user = User.builder()
      .email(request.getEmail().toLowerCase())
      .passwordHash(hashedPassword)
      .firstName(request.getFirstName())
      .lastName(request.getLastName())
      .phone(request.getPhone())
      .role(request.getRole())
      .branchId(request.getBranchId())
      .createdBy(UUID.fromString(createdByUserId))
      .build();

    User savedUser = userRepository.save(user);

    log.info("Usuario creado exitosamente: {} en tenant: {}",
      savedUser.getEmail(), tenantSlug);

    return CreateUserResponse.builder()
      .user(toUserResponse(savedUser))
      .temporaryPassword(temporaryPassword)
      .instructionMessage(
        getMessage("auth.temporary.password.instruction"))
      .build();
  }

  // ─── MANEJAR LOGIN FALLIDO ────────────────────────────────────
  private void handleFailedLogin(User user) {
    // Siempre incrementamos primero
    userRepository.incrementFailedAttempts(user.getId());
    int newFailedAttempts = user.getFailedAttempts() + 1;

    if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
      OffsetDateTime lockedUntil = OffsetDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
      userRepository.lockAccount(user.getId(), lockedUntil);
      log.warn("Cuenta bloqueada {} minutos: {}", LOCK_DURATION_MINUTES, user.getEmail());

      // Lanzar AccountLockedException en el intento 5
      // El cliente sabe inmediatamente que está bloqueado
      throw new AccountLockedException(getMessage("auth.account.locked", LOCK_DURATION_MINUTES));
    }
  }

  // ─── GENERAR CONTRASEÑA TEMPORAL ──────────────────────────────
  private String generateTemporaryPassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder password = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      password.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return password.toString();
  }

  // ─── CONVERTIR ENTITY A RESPONSE ──────────────────────────────
  private UserResponse toUserResponse(User user) {
    return UserResponse.builder()
      .id(user.getId())
      .email(user.getEmail())
      .firstName(user.getFirstName())
      .lastName(user.getLastName())
      .fullName(user.getFullName())
      .phone(user.getPhone())
      .role(user.getRole())
      .branchId(user.getBranchId())
      .avatarUrl(user.getAvatarUrl())
      .active(user.isActive())
      .mustChangePassword(user.isMustChangePassword())
      .lastLoginAt(user.getLastLoginAt())
      .passwordChangedAt(user.getPasswordChangedAt())
      .createdAt(user.getCreatedAt())
      .build();
  }

}
