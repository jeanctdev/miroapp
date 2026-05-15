package com.miroapp.tenant.controller;

import com.miroapp.common.response.ApiResponse;
import com.miroapp.tenant.dto.TenantRegisterRequest;
import com.miroapp.tenant.dto.TenantResponse;
import com.miroapp.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// =====================================================================
// TenantController — endpoints REST para gestión de tenants
//
// @RestController → combina @Controller + @ResponseBody
//                   todos los métodos retornan JSON automáticamente
//
// @RequestMapping → prefijo de URL para todos los endpoints
//                   todos empiezan con /api/tenants
//
// @RequiredArgsConstructor → Lombok inyecta TenantService
//
// @Slf4j → logger automático de Lombok
// =====================================================================
@Slf4j
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

  private final TenantService tenantService;

  // ─── POST /api/tenants/register ───────────────────────────────
  // Registra una nueva empresa cliente en Miro
  //
  // @PostMapping → solo acepta HTTP POST
  // @RequestBody → el body del request se convierte a DTO
  // @Valid → activa las validaciones del DTO automáticamente
  //          si falla → GlobalExceptionHandler retorna 400
  //
  // ResponseEntity<ApiResponse<TenantResponse>>:
  // → ResponseEntity → nos permite controlar el código HTTP
  // → ApiResponse    → el sobre estándar de Miro
  // → TenantResponse → los datos del tenant creado
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<TenantResponse>> registerTenant(
    @Valid @RequestBody TenantRegisterRequest request) {

    log.info("Request recibido para registrar tenant: {}", request.getSlug());

    TenantResponse response = tenantService.registerTenant(request);

    // 201 Created → se creó un nuevo recurso
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.ok(
        response,
        "/api/tenants/register",
        HttpStatus.CREATED.value()
      ));
  }

  // ─── GET /api/tenants/{slug} ──────────────────────────────────
  // Obtiene los datos de un tenant por su slug
  //
  // @GetMapping → solo acepta HTTP GET
  // @PathVariable → extrae el slug de la URL
  //   GET /api/tenants/venedog → slug = "venedog"
  @GetMapping("/{slug}")
  public ResponseEntity<ApiResponse<TenantResponse>> getTenantBySlug(
    @PathVariable String slug) {

    log.debug("Request recibido para obtener tenant: {}", slug);

    TenantResponse response = tenantService.getTenantBySlug(slug);

    // 200 OK → consulta exitosa
    return ResponseEntity
      .status(HttpStatus.OK)
      .body(ApiResponse.ok(
        response,
        "/api/tenants/" + slug,
        HttpStatus.OK.value()
      ));
  }

}
