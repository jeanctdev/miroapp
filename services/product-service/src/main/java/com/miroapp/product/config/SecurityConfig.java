package com.miroapp.product.config;

import com.miroapp.product.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// =====================================================================
// SecurityConfig — configuración de seguridad del product-service
// =====================================================================
// @EnableWebSecurity    → activa Spring Security para MVC/Servlet
// @EnableMethodSecurity → habilita @PreAuthorize en los controllers
//                         para control de acceso por rol:
//                         @PreAuthorize("hasRole('TENANT_ADMIN')")
//
// Todos los endpoints de product-service requieren JWT válido.
// No hay paths públicos — el catálogo es privado por tenant.
//
// El gateway ya validó el JWT antes de llegar aquí.
// JwtAuthenticationFilter lee los headers X-Tenant-Slug que
// el gateway propagó y los registra en el SecurityContext.
// =====================================================================
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http)
    throws Exception {

    return http
      // ── CSRF deshabilitado ────────────────────────────────
      // API REST con JWT stateless — CSRF no aplica.
      .csrf(AbstractHttpConfigurer::disable)
      // ── STATELESS — sin sesiones HTTP ─────────────────────
      // JWT se valida en cada request.
      // El servidor no guarda estado de sesión.
      .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      // ── Todos los endpoints requieren autenticación ───────
      // No hay paths públicos en product-service.
      // El gateway ya filtró los requests sin JWT.
      .authorizeHttpRequests(auth -> auth
        .anyRequest().authenticated())
      // ── JwtAuthenticationFilter antes del filtro de Spring ─
      // Lee X-Tenant-Slug y registra en SecurityContext.
      .addFilterBefore(
        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
      .build();
  }

  // Agregar este bean dentro de SecurityConfig
  @Bean
  public UserDetailsService userDetailsService() {
    // Bean vacío — product-service no autentica usuarios directamente.
    // La autenticación la hace auth-service.
    // El gateway valida el JWT y propaga los headers.
    // Este bean evita que Spring Security genere una password por defecto.
    return new InMemoryUserDetailsManager();
  }

}
