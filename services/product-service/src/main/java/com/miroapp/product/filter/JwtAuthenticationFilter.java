package com.miroapp.product.filter;

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
// JwtAuthenticationFilter — filtro JWT del product-service (MVC)
// =====================================================================
// El gateway ya validó el JWT y propagó los headers:
//   X-Tenant-Slug → schema de PostgreSQL donde operar
//   X-User-Id     → UUID del usuario autenticado
//   X-User-Role   → rol para @PreAuthorize
//   X-Branch-Id   → sucursal activa
//   X-User-Email  → email del usuario
//
// Este filtro lee esos headers y registra la autenticación
// en el SecurityContext para que @PreAuthorize funcione.
//
// ¿Por qué leer headers en vez de validar el JWT directamente?
// El gateway es la única puerta de entrada — garantiza que
// solo requests con JWT válido llegan aquí.
// Volver a validar el JWT sería redundante.
// Leer los headers propagados es más eficiente.
// =====================================================================
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
                                  throws ServletException, IOException {
    // ── Leer headers propagados por el gateway ────────────────
    String userId     = request.getHeader("X-User-Id");
    String role       = request.getHeader("X-User-Role");
    String tenantSlug = request.getHeader("X-Tenant-Slug");
    String branchId   = request.getHeader("X-Branch-Id");
    String email      = request.getHeader("X-User-Email");

    // Si no hay headers → el request no pasó por el gateway
    // o el gateway no propagó el contexto → dejamos pasar
    // y Spring Security rechazará con 401 si el endpoint
    // requiere autenticación.
    if (userId == null || role == null || tenantSlug == null) {
      log.warn("Request sin headers de contexto: {}",
        request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    log.debug("Request autenticado: user={} tenant={} role={}",
      email, tenantSlug, role);

    // ── Registrar en SecurityContext ──────────────────────────
    // principal   = userId (para auditoría)
    // credentials = tenantSlug (para SET search_path)
    // authorities = ["ROLE_TENANT_ADMIN"] (para @PreAuthorize)
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(
        userId,
        tenantSlug,
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
      );

    authentication.setDetails(tenantSlug);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }
}
