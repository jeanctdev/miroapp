package com.miroapp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

// =====================================================================
// SecurityConfig — Configuración de seguridad del gateway
// =====================================================================
// ¿Por qué @EnableWebFluxSecurity y no @EnableWebSecurity?
//
//   @EnableWebFluxSecurity → activa Spring Security para WebFlux
//                            usa ServerHttpSecurity + SecurityWebFilterChain
//                            funciona con Netty
//
// El gateway usa Netty (reactivo) → DEBE usar @EnableWebFluxSecurity.
// Si usáramos @EnableWebSecurity aquí → error al arrancar.
//
// ¿Por qué necesitamos SecurityConfig si ya tenemos JwtGatewayFilter?
//
//   Cuando Spring Security está en el classpath, se activa
//   automáticamente y bloquea TODOS los endpoints con autenticación
//   básica HTTP por defecto. Sin SecurityConfig, ni /api/auth/login
//   funcionaría — Spring Security lo bloquearía antes de que llegue
//   al JwtGatewayFilter.
//
//   SecurityConfig le dice a Spring Security exactamente qué
//   permitir y qué proteger, y registra nuestro JwtGatewayFilter
//   en el lugar correcto de la cadena de seguridad.
// =====================================================================
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
    ServerHttpSecurity http) {
    return http

      // ── CSRF deshabilitado ────────────────────────────────
      // CSRF (Cross-Site Request Forgery) protege aplicaciones
      // que usan sesiones HTTP y cookies de sesión.
      //
      // MIRO usa JWT stateless:
      //   → No hay sesión en el servidor
      //   → No hay cookie de sesión que robar
      //   → El JWT viaja en el header Authorization
      //
      // CSRF no aplica para APIs REST con JWT.
      // Si lo dejamos habilitado → todos los POST/PUT/DELETE
      // fallarían porque no tienen el token CSRF en el body.
      .csrf(ServerHttpSecurity.CsrfSpec::disable)
      // ── CORS ──────────────────────────────────────────────
      // Le decimos a Spring Security que respete la
      // configuración de CORS que ya definimos en
      // application-local.yaml (spring.cloud.gateway.globalcors).
      //
      // Sin esta línea, Spring Security interceptaría las
      // peticiones OPTIONS del preflight ANTES de que lleguen
      // al gateway y las bloquearía, rompiendo el CORS.
      .cors(corsSpec -> {})
      // ── Deshabilitar login de formulario ──────────────────
      // Spring Security por defecto muestra una página HTML
      // de login cuando no hay autenticación.
      // MIRO es una API REST — no tiene páginas HTML.
      // Deshabilitamos el formulario para que devuelva 401 JSON.
      .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
      // ── Deshabilitar HTTP Basic ───────────────────────────
      // HTTP Basic = enviar usuario:contraseña en cada request
      // codificado en Base64 en el header Authorization.
      // MIRO usa JWT — HTTP Basic no aplica.
      // Sin deshabilitar esto, Spring Security intentaría
      // interpretar el JWT como credenciales Basic y fallaría.
      .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
      // ── STATELESS — sin sesiones HTTP ─────────────────────
      // Sin esta línea Spring Security busca el SecurityContext
      // en la WebSession y siempre dice:
      // "No SecurityContext found in WebSession" → 401
      // Con esta línea usa el ReactiveSecurityContextHolder
      // que JwtGatewayFilter popula con el JWT validado.
      .securityContextRepository(
        NoOpServerSecurityContextRepository.getInstance())
      // ── TODOS LOS PATHS PERMITIDOS ────────────────────────
      // Spring Security permite todo — JwtGatewayFilter
      // es quien decide qué pasa y qué no.
      // Sin esto Spring Security rechaza antes de que
      // nuestro filtro pueda actuar.
      .authorizeExchange(ex -> ex
        .anyExchange().permitAll()
      )
      .build();
  }

  // ================================================================
  // ReactiveUserDetailsService vacío
  // ================================================================
  // ¿Por qué este bean?
  // Spring Security detecta que no hay UserDetailsService configurado
  // y genera automáticamente un usuario con contraseña aleatoria
  // mostrando el WARN "Using generated security password".
  //
  // El gateway NO autentica usuarios directamente — eso lo hace
  // auth-service. El gateway solo valida JWT con JwtGatewayFilter.
  //
  // Este bean le dice a Spring Security explícitamente que no hay
  // UserDetailsService — es intencional, no un olvido.
  // Elimina el WARN y la generación de contraseña por defecto.
  // ================================================================
  @Bean
  public ReactiveUserDetailsService userDetailsService() {
    return username -> reactor.core.publisher.Mono.empty();
  }

}
