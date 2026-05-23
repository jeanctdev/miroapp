package com.miroapp.gateway.filter;

import com.miroapp.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

// =====================================================================
// JwtGatewayFilter — Filtro JWT global del gateway
// =====================================================================
// ¿Qué es GlobalFilter?
//   Se ejecuta para CADA request que pasa por el gateway.
//   No importa a qué microservicio va — este filtro lo intercepta.
//
// ¿Qué es Ordered?
//   Define la prioridad de ejecución entre filtros.
//   HIGHEST_PRECEDENCE = se ejecuta PRIMERO, antes que cualquier
//   otro filtro de Spring Cloud Gateway.
//   Es crítico que el JWT se valide antes que nada más procese
//   el request.
//
// Diferencia clave con JwtAuthenticationFilter de security-lib:
//   security-lib → extiende OncePerRequestFilter (mundo MVC/Servlet)
//                  usa HttpServletRequest, HttpServletResponse
//   Este filtro  → implementa GlobalFilter (mundo WebFlux/reactivo)
//                  usa ServerWebExchange, Mono<Void>
//
// La lógica es la misma — solo cambia el API que se usa.
// JwtUtil sí se reutiliza porque solo manipula Strings y no
// depende del mundo Servlet ni del mundo WebFlux.
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

  // JwtUtil de security-lib — valida tokens y extrae claims.
  // Es un @Component normal que Spring inyecta aquí.
  // No depende de MVC ni de WebFlux — solo usa JJWT y Strings.
  private final JwtUtil jwtUtil;

  // MessageSource — obtiene mensajes de messages.properties.
  private final MessageSource messageSource;

  // ─── PATHS PÚBLICOS ───────────────────────────────────────────
  // Estos paths NO requieren JWT.
  // El gateway los deja pasar directamente al microservicio destino.
  //
  // ¿Por qué una lista y no el SecurityConfig solo?
  // El filtro actúa ANTES que Spring Security.
  // Si un path es público, el filtro ni siquiera intenta leer el JWT.
  // Esto evita errores de "token no encontrado" en paths públicos.
  private static final List<String> PUBLIC_PATHS = List.of(
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/tenants/register",  // registrar empresa nueva (sin tenant aún)
    "/actuator"               // monitoreo — solo accesible en red interna
  );

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // ── PASO 1: OPTIONS siempre pasa ─────────────────────────
    // El navegador envía OPTIONS antes de cualquier request
    // con headers custom (Authorization, Content-Type).
    // Si OPTIONS requiere JWT → el preflight falla →
    // el frontend nunca puede enviar el JWT real.
    if (HttpMethod.OPTIONS.equals(request.getMethod())) {
      log.debug("OPTIONS request, pasando sin JWT: {}", path);
      return chain.filter(exchange);
    }

    log.debug("Gateway procesando: {} {}", request.getMethod(), path);

    // ── PASO 2:
    // Si es público, pasamos directamente al siguiente filtro
    // sin validar JWT. El microservicio destino se encarga
    // de su propia lógica (ej: auth-service valida credenciales).
    if (isPublicPath(path)) {
      log.debug("Path publico, pasando sin validar JWT: {}", path);
      return chain.filter(exchange);
    }

    // ── PASO 3: Leer el header Authorization ─────────────────
    String authHeader = request.getHeaders()
      .getFirst(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("Request sin token JWT rechazado: {}", path);
      return rejectWithUnauthorized(exchange, "gateway.error.token.missing");
    }
    // ── PASO 4: Extraer el token ──────────────────────────────
    String token = authHeader.substring(7);

    // ── PASO 5: Validar el token ──────────────────────────────
    // JwtUtil verifica firma HMAC y que no esté expirado.
    if (!jwtUtil.validateToken(token)) {
      log.warn("Token JWT invalido o expirado en path: {}", path);
      return rejectWithUnauthorized(exchange, "gateway.error.token.invalid");
    }

    // ── PASO 6: Extraer claims del token ─────────────────────
    // Claims = datos guardados en el JWT al hacer login.
    String userId     = jwtUtil.extractUserId(token);
    String tenantSlug = jwtUtil.extractTenantSlug(token);
    String role       = jwtUtil.extractRole(token);
    String branchId   = jwtUtil.extractBranchId(token);
    String email      = jwtUtil.extractEmail(token);

    log.debug("JWT valido — userId={} tenant={} role={}",
      userId, tenantSlug, role);

    // ── PASO 7: Propagar contexto del tenant como headers ─────
    // Los microservicios usan estos headers para:
    //   → SET search_path TO "venedog" en PostgreSQL
    //   → Saber qué usuario y rol está operando
    //   → Auditoría de operaciones
    //
    // El microservicio confía en estos headers porque SOLO
    // el gateway puede ponerlos (red interna Docker).
    ServerHttpRequest mutatedRequest = request.mutate()
      .header("X-Tenant-Slug", tenantSlug)
      .header("X-User-Id",     userId)
      .header("X-User-Role",   role)
      .header("X-Branch-Id",   branchId != null ? branchId : "")
      .header("X-User-Email",  email    != null ? email    : "")
      .build();

    // ── PASO 8: Continuar con el request modificado ───────────
    ServerWebExchange mutatedExchange = exchange.mutate()
      .request(mutatedRequest)
      .build();

    // ── PASO 9: Spring Cloud Gateway enruta al microservicio ──
    return chain.filter(mutatedExchange);
  }

  @Override
  public int getOrder() {
    // HIGHEST_PRECEDENCE = Integer.MIN_VALUE
    // Máxima prioridad — se ejecuta antes que cualquier otro filtro.
    return Ordered.HIGHEST_PRECEDENCE;
  }

  // ─── MÉTODOS PRIVADOS ────────────────────────────────────────

  // Verifica si el path del request es público (no requiere JWT).
  // Usa startsWith para cubrir sub-paths automáticamente.
  // Ejemplo: /api/auth/login empieza con /api/auth/login → público.
  //          /api/auth/users NO empieza con ningún path público → protegido.
  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.stream()
      .anyMatch(path::startsWith);
  }

  // Rechaza el request con 401 UNAUTHORIZED.
  // messageKey → clave del messages.properties
  //   "gateway.error.token.missing"
  //   "gateway.error.token.invalid"
  //   "gateway.error.token.unauthorized"
  //
  // getMessage() busca el mensaje en messages.properties
  // según el Locale del request (por defecto es_PE en MIRO).
  // NUNCA se pasan strings literales como mensaje.
  private Mono<Void> rejectWithUnauthorized(ServerWebExchange exchange,
                                            String messageKey) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String message = messageSource.getMessage(
      messageKey,
      null,
      LocaleContextHolder.getLocale()
    );

    log.warn("Acceso denegado [{}]: {}", messageKey, message);

    return response.setComplete();
  }


}
