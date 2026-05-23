package com.miroapp.auth.config;

import com.miroapp.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// =====================================================================
// SecurityConfig — configuración central de Spring Security
//
// @Configuration  → clase de configuración de Spring
// @EnableWebSecurity → activa Spring Security en la aplicación
// @EnableMethodSecurity → activa @PreAuthorize en los Controllers
//
// ¿Qué configura?
// → Qué rutas son públicas (sin JWT)
// → Qué rutas requieren autenticación (con JWT)
// → Política de sesiones (stateless con JWT)
// → BCryptPasswordEncoder para hashear contraseñas
// → Dónde se ejecuta el JwtAuthenticationFilter
// =====================================================================
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  // ─── SECURITY FILTER CHAIN ────────────────────────────────────
  // Define las reglas de seguridad para todos los endpoints
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http)
    throws Exception {

    http
      // ── CSRF ──────────────────────────────────────────────
      // Cross-Site Request Forgery
      // Desactivado porque usamos JWT — no cookies
      // CSRF protege aplicaciones web con sesiones y cookies
      // Con JWT stateless → CSRF no aplica
      .csrf(AbstractHttpConfigurer::disable)

      // ── CORS ──────────────────────────────────────────────
      // Cross-Origin Resource Sharing
      // Permite que el frontend (React/Vue) llame a la API
      // desde un dominio diferente
      // Configuración básica — en Sprint 3 la refinamos
      .cors(AbstractHttpConfigurer::disable)

      // ── AUTORIZACIÓN POR ENDPOINT ─────────────────────────
      .authorizeHttpRequests(auth -> auth
        // RUTAS PÚBLICAS — sin JWT
        // Cualquiera puede llamar a estos endpoints
        .requestMatchers(
          "POST", "/api/auth/login"
        ).permitAll()
        // Actuator — para health checks de Azure
        .requestMatchers("/actuator/**").permitAll()
        // TODAS LAS DEMÁS RUTAS → requieren JWT válido
        .anyRequest().authenticated()
      )

      // ── POLÍTICA DE SESIONES ──────────────────────────────
      // STATELESS → Spring Security no crea sesiones HTTP
      // Cada request es independiente
      // La autenticación viaja en el JWT, no en la sesión
      .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )

      // ── JWT FILTER ────────────────────────────────────────
      // Agrega nuestro filtro ANTES del filtro de Spring
      // UsernamePasswordAuthenticationFilter es el filtro
      // por defecto de Spring para usuario/contraseña
      // Nuestro filtro va antes para interceptar el JWT
      .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // ─── BCRYPT PASSWORD ENCODER ──────────────────────────────────
  // Bean de BCrypt para usar en auth-service
  // strength 12 → balance seguridad/performance
  // @Bean → Spring lo gestiona y lo inyecta donde se necesite
  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  // ─── AUTHENTICATION MANAGER ───────────────────────────────────
  // Gestiona el proceso de autenticación de Spring Security
  // Lo usamos en AuthService para verificar credenciales
  // Spring lo configura automáticamente con AuthenticationConfiguration
  @Bean
  public AuthenticationManager authenticationManager(
    AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
