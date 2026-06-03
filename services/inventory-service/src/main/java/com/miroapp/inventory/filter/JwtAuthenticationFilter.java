package com.miroapp.inventory.filter;

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
// JwtAuthenticationFilter — lee headers propagados por el gateway
// =====================================================================
// El gateway ya validó el JWT y propagó estos headers:
//   X-Tenant-Slug → schema de PostgreSQL donde operar
//   X-User-Id     → UUID del usuario autenticado
//   X-User-Role   → rol para @PreAuthorize
//   X-Branch-Id   → sucursal activa
//
// Este filtro los lee y registra en el SecurityContext:
//   principal   = userId   → para auditoría
//   credentials = tenantSlug → para SET search_path
//   authorities = [ROLE_X] → para @PreAuthorize
//
// ¿Por qué leer headers y no validar el JWT directamente?
//   El gateway es la única puerta de entrada.
//   Solo él puede poner estos headers en la red interna.
//   Volver a validar el JWT sería redundante e ineficiente.
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain)
    throws ServletException, IOException {

    String userId     = request.getHeader("X-User-Id");
    String role       = request.getHeader("X-User-Role");
    String tenantSlug = request.getHeader("X-Tenant-Slug");
    String email      = request.getHeader("X-User-Email");

    // Sin headers → request no pasó por el gateway
    // Spring Security rechazará con 401 si el endpoint
    // requiere autenticación
    if (userId == null || role == null || tenantSlug == null) {
      log.warn("Request sin headers de contexto: {}",
        request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    log.debug("Request autenticado: user={} tenant={} role={}",
      email, tenantSlug, role);

    // Registrar en SecurityContext para @PreAuthorize
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
        userId,
        tenantSlug,
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
      );

    authentication.setDetails(tenantSlug);
    SecurityContextHolder.getContext()
      .setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }

}
