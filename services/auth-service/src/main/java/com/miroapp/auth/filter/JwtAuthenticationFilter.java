package com.miroapp.auth.filter;

import com.miroapp.security.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// =====================================================================
// JwtAuthenticationFilter — filtro JWT del auth-service (mundo MVC)
// =====================================================================
// ¿Por qué está aquí y no en security-lib?
//
// OncePerRequestFilter necesita jakarta.servlet.Filter que pertenece
// al mundo Servlet/MVC. El gateway usa WebFlux — jakarta.servlet
// no existe en su classpath. Si estuviera en security-lib, el
// gateway explotaría al arrancar.
//
// Cada microservicio MVC tiene su propia copia de este filtro.
// El gateway tiene su propio JwtGatewayFilter con GlobalFilter
// que es el equivalente reactivo.
//
// JwtUtil sí está en security-lib porque es independiente —
// solo usa JJWT y Strings, sin dependencia de Servlet ni WebFlux.
//
// OncePerRequestFilter → se ejecuta exactamente UNA vez por request.
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  // JwtUtil de security-lib — valida tokens y extrae claims.
  // Es el único componente compartido que usamos de security-lib.
  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain) throws ServletException, IOException {

    // ── PASO 1: Leer el header Authorization ─────────────────
    String authHeader = request.getHeader("Authorization");

    // Si no hay header o no empieza con "Bearer "
    // → no hay JWT → dejamos pasar al siguiente filtro.
    // Spring Security decidirá si el endpoint requiere auth.
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    // ── PASO 2: Extraer el token ──────────────────────────────
    // "Bearer eyJhbGci..." → "eyJhbGci..."
    String token = authHeader.substring(7);

    // ── PASO 3: Validar el token ──────────────────────────────
    if (!jwtUtil.validateToken(token)) {
      log.warn("Token JWT invalido en: {}", request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    // ── PASO 4: Extraer claims del token ─────────────────────
    String userId     = jwtUtil.extractUserId(token);
    String role       = jwtUtil.extractRole(token);
    String tenantSlug = jwtUtil.extractTenantSlug(token);
    String email      = jwtUtil.extractEmail(token);

    log.debug("Request autenticado: user={} tenant={} role={}",
      email, tenantSlug, role);

    // ── PASO 5: Registrar en SecurityContext ──────────────────
    // Spring Security usa esto para @PreAuthorize.
    // "ROLE_TENANT_ADMIN" → hasRole('TENANT_ADMIN')
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
        userId,   // principal → UUID del usuario
        token,    // credentials → el token
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
      );

    // tenantSlug en details → el Service lo usa para
    // SET search_path TO "venedog" en PostgreSQL
    authentication.setDetails(tenantSlug);

    SecurityContextHolder.getContext()
      .setAuthentication(authentication);

    // ── PASO 6: Continuar con el siguiente filtro ─────────────
    filterChain.doFilter(request, response);
  }
}
