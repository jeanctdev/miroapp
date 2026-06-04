package com.miroapp.tenant.service;

import com.miroapp.tenant.dto.TenantRegisterRequest;
import com.miroapp.tenant.dto.TenantResponse;
import com.miroapp.tenant.entity.Tenant;
import com.miroapp.tenant.exception.TenantAlreadyExistsException;
import com.miroapp.tenant.exception.TenantNotFoundException;
import com.miroapp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// =====================================================================
// TenantService — lógica de negocio para gestión de tenants
//
// @Service → marca esta clase como componente de negocio
//            Spring la instancia automáticamente
//
// @RequiredArgsConstructor → Lombok genera el constructor
//            con todos los campos final
//            Es la forma correcta de inyección de dependencias
//            en Spring Boot moderno (en lugar de @Autowired)
//
// @Slf4j → Lombok genera el logger automáticamente
//          Puedes usar: log.info(), log.debug(), log.error()
// Flujo de registro completo:
// 1. Validar slug y email únicos
// 2. Guardar en public.tenants
// 3. Crear schema con Flyway (TenantSchemaService)
// 4. Crear TENANT_ADMIN en {tenant}.users (UserCreationService)
// 5. Retornar datos + contraseña temporal
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

  private final TenantRepository tenantRepository;
  private final TenantSchemaService tenantSchemaService;
  private final UserCreationService userCreationService;
  private final MessageSource messageSource;

  // ─── HELPER ───────────────────────────────────────────────────
  // Obtiene mensaje del properties con parámetros opcionales
  private String getMessage(String code, Object... args) {
    return messageSource.getMessage(
      code,
      args,
      code,
      LocaleContextHolder.getLocale()
    );
  }

  // ─── REGISTRAR TENANT ─────────────────────────────────────────
  // @Transactional → si algo falla → rollback automático
  // Todo el método es una sola transacción ACID
  @Transactional
  public TenantResponse registerTenant(TenantRegisterRequest request){
    log.info("Iniciando registro de tenant con slug: {}", request.getSlug());

    // ── VALIDACIÓN 1: slug único ───────────────────────────────
    if (tenantRepository.existsBySlug(request.getSlug())) {
      log.warn("Intento de registro con slug duplicado: {}", request.getSlug());
      throw new TenantAlreadyExistsException(getMessage("tenant.already.exists.slug",
        request.getSlug()), "slug");
    }

    // ── VALIDACIÓN 1: email único ───────────────────────────────
    if (tenantRepository.existsByAdminEmail(request.getAdminEmail())) {
      log.warn("Intento de registro con email duplicado: {}", request.getAdminEmail());
      throw new TenantAlreadyExistsException(
        getMessage("tenant.already.exists.email",
          request.getAdminEmail()), "adminEmail");
    }

    // ── CREAR LA ENTITY ────────────────────────────────────────
    // Convertimos el DTO a Entity usando el Builder de Lombok
    // @PrePersist se ejecutará automáticamente al guardar:
    // → asigna status = TRIAL
    // → asigna trialEndsAt = ahora + 14 días
    // → asigna createdAt y updatedAt
    Tenant tenant = Tenant.builder()
      .slug(request.getSlug())
      .name(request.getName())
      .countryCode(request.getCountryCode().toUpperCase())
      .currencyCode(request.getCurrencyCode().toUpperCase())
      .planId(request.getPlanId())
      .adminEmail(request.getAdminEmail().toLowerCase())
      .adminName(request.getAdminName())
      .adminPhone(request.getAdminPhone())
      .taxInfo(request.getTaxInfo())
      .build();

    // ── GUARDAR EN LA BD ───────────────────────────────────────
    // save() ejecuta el INSERT en public.tenants
    // @PrePersist se ejecuta aquí automáticamente
    Tenant savedTenant = tenantRepository.save(tenant);

    // ── CREAR SCHEMA DEL TENANT ───────────────────────────────
    tenantSchemaService.createTenantSchema(savedTenant.getSlug());

    //// ── CREAR SUCURSAL PRINCIPAL ──────────────────────────────
    tenantSchemaService.createMainBranch(savedTenant.getSlug());

    // ── CREAR TENANT_ADMIN ────────────────────────────────────
    // Retorna la contraseña temporal en texto plano
    // Es la ÚNICA vez que sale del sistema
    String temporaryPassword = userCreationService.createTenantAdmin(
      savedTenant.getSlug(),
      savedTenant.getAdminEmail(),
      savedTenant.getAdminName()
    );

    log.info("Tenant registrado exitosamente: {} con ID: {}",
      savedTenant.getSlug(), savedTenant.getId());

    // ── RETORNAR RESPONSE ──────────────────────────────────────
    return toResponse(savedTenant, temporaryPassword);
  }

  // ─── OBTENER TENANT POR SLUG ──────────────────────────────────
  // Solo lectura → no necesita @Transactional
  // readOnly = true → optimización de performance
  @Transactional(readOnly = true)
  public TenantResponse getTenantBySlug(String slug) {
    log.debug("Buscando tenant con slug: {}", slug);

    Tenant tenant = tenantRepository
      .findActiveTenantBySlug(slug)
      .orElseThrow(() -> new TenantNotFoundException(getMessage("tenant.not.found", slug)));

    return toResponse(tenant, null);
  }

  // ─── CONVERTIR ENTITY A RESPONSE ──────────────────────────────
  // Método privado — solo lo usa este Service
  // Convierte la Entity (BD) al DTO (lo que ve el cliente)
  // Calcula el mensaje del trial dinámicamente
  private TenantResponse toResponse(Tenant tenant, String temporaryPassword) {
    // Calculamos los días restantes del trial
    String trialMessage = null;
    if (tenant.getTrialEndsAt() != null) {
      //long daysLeft = ChronoUnit.DAYS.between(OffsetDateTime.now(), tenant.getTrialEndsAt());
      long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getTrialEndsAt().toLocalDate());
      if (daysLeft > 0) {
        // suffix para singular/plural en español
        String suffix = daysLeft == 1 ? "" : "s";
        trialMessage = getMessage(
          "tenant.trial.days.remaining",
          daysLeft,
          suffix,
          suffix
        );
      } else {
        trialMessage = getMessage("tenant.trial.expired");
      }
    }

    // Mensaje de instrucciones — solo al registrarse
    String instructionMessage = temporaryPassword != null
      ? getMessage("tenant.registration.instruction")
      : null;

    return TenantResponse.builder()
      .id(tenant.getId())
      .slug(tenant.getSlug())
      .name(tenant.getName())
      .countryCode(tenant.getCountryCode())
      .currencyCode(tenant.getCurrencyCode())
      .planId(tenant.getPlanId())
      .adminEmail(tenant.getAdminEmail())
      .adminName(tenant.getAdminName())
      .adminPhone(tenant.getAdminPhone())
      .taxInfo(tenant.getTaxInfo())
      .status(tenant.getStatus())
      .trialEndsAt(tenant.getTrialEndsAt())
      .createdAt(tenant.getCreatedAt())
      .updatedAt(tenant.getUpdatedAt())
      .trialMessage(trialMessage)
      .temporaryPassword(temporaryPassword)
      .instructionMessage(instructionMessage)
      .build();
  }

}
