package com.miroapp.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
// JwtAuthenticationFilter — intercepta cada request y valida el JWT
//
// Extiende OncePerRequestFilter → se ejecuta UNA VEZ por request
// No importa cuántos filtros haya — este se ejecuta exactamente una vez
//
// ¿Qué hace?
// 1. Lee el header Authorization: Bearer {token}
// 2. Extrae el token
// 3. Valida el token con JwtUtil
// 4. Si es válido → registra al usuario en el SecurityContext
// 5. El Controller puede acceder al usuario autenticado
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain) throws ServletException, IOException {

    // PASO 1 — Extraer el header Authorization
    String authHeader = request.getHeader("Authorization");

    // Si no hay header o no empieza con "Bearer "
    // → no hay JWT → dejamos pasar (Spring Security
    //   decidirá si el endpoint requiere autenticación)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    // PASO 2 — Extraer el token del header
    // "Bearer eyJhbGci..." → "eyJhbGci..."
    String token = authHeader.substring(7);

    // PASO 3 — Validar el token
    if (!jwtUtil.validateToken(token)) {
      log.warn("Token JWT inválido en request: {}", request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    // PASO 4 — Extraer datos del token
    String userId     = jwtUtil.extractUserId(token);
    String role       = jwtUtil.extractRole(token);
    String tenantSlug = jwtUtil.extractTenantSlug(token);
    String email      = jwtUtil.extractEmail(token);

    log.debug("Request autenticado: user={} tenant={} role={}", email, tenantSlug, role);

    // PASO 5 — Registrar al usuario en el SecurityContext
    // SimpleGrantedAuthority → el rol del usuario
    // Spring Security usa esto para @PreAuthorize
    // "ROLE_TENANT_ADMIN" → @PreAuthorize("hasRole('TENANT_ADMIN')")
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
        userId,    // principal → quién es
        token,     // credentials → el token
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
      );

    // Guardamos info adicional en los detalles
    // Los Controllers pueden acceder a esto
    authentication.setDetails(tenantSlug);

    // Registra la autenticación en el contexto de Spring Security
    // A partir de aquí → el request está autenticado
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // PASO 6 — Continuar con el siguiente filtro
    filterChain.doFilter(request, response);
  }
}
