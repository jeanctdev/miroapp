package com.miroapp.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

// =====================================================================
// JwtUtil — genera y valida tokens JWT
//
// ¿Qué es un JWT?
// JSON Web Token — token firmado digitalmente que contiene
// información del usuario. El servidor lo genera al hacer login
// y el cliente lo envía en cada request.
//
// Estructura del JWT de Miro:
// HEADER.PAYLOAD.SIGNATURE
//
// PAYLOAD contiene:
// {
//   "sub": "user_uuid",
//   "tenantSlug": "venedog",
//   "role": "TENANT_ADMIN",
//   "branchId": null,
//   "email": "admin@venedog.com",
//   "mustChangePassword": true,
//   "iat": timestamp_creacion,
//   "exp": timestamp_expiracion
// }
//
// @Component → Spring lo gestiona como componente
// @Value → Lee valores del application-local.yaml
// =====================================================================
@Slf4j
@Component
public class JwtUtil {

  // Clave secreta para firmar el JWT
  // Viene de application-local.yaml → jwt.secret
  // En producción → variable de entorno en Azure
  @Value("${jwt.secret}")
  private String secret;

  // Tiempo de vida del token en milisegundos
  // Viene de application-local.yaml → jwt.expiration
  // Default: 86400000 = 24 horas
  @Value("${jwt.expiration}")
  private long expiration;

  // ─── GENERAR SECRET KEY ───────────────────────────────────────
  // Convierte el String secret en una SecretKey para JJWT
  // HMAC-SHA256 → algoritmo de firma simétrico
  // La misma clave firma y verifica
  private SecretKey getSigningKey() {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  // ─── GENERAR TOKEN ────────────────────────────────────────────
  // Se llama al hacer login exitoso
  // Retorna el JWT como String
  public String generateToken(
    UUID userId,
    String tenantSlug,
    String role,
    UUID branchId,
    String email,
    boolean mustChangePassword) {

    Date now = new Date();
    Date expirationDate = new Date(now.getTime() + expiration);

    return Jwts.builder()
      // sub → identificador del usuario
      .subject(userId.toString())
      // Claims personalizados de Miro
      .claim("tenantSlug", tenantSlug)
      .claim("role", role)
      .claim("branchId", branchId != null
        ? branchId.toString() : null)
      .claim("email", email)
      .claim("mustChangePassword", mustChangePassword)
      // Fechas
      .issuedAt(now)
      .expiration(expirationDate)
      // Firma con HMAC-SHA256
      .signWith(getSigningKey())
      .compact();
  }

  // ─── VALIDAR TOKEN ────────────────────────────────────────────
  // Verifica que el token:
  // → Fue firmado con nuestra clave secreta
  // → No ha expirado
  // Retorna true si es válido, false si no
  public boolean validateToken(String token) {
    try {
      Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      log.warn("Token JWT inválido: {}", e.getMessage());
      return false;
    }
  }

  // ─── EXTRAER CLAIMS ───────────────────────────────────────────
  // Claims → los datos guardados dentro del token
  // Se extraen en cada request autenticado
  private Claims extractClaims(String token) {
    return Jwts.parser()
      .verifyWith(getSigningKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  // ─── MÉTODOS DE EXTRACCIÓN ────────────────────────────────────
  // Cada método extrae un dato específico del token
  public String extractUserId(String token) {
    return extractClaims(token).getSubject();
  }

  public String extractTenantSlug(String token) {
    return extractClaims(token).get("tenantSlug", String.class);
  }

  public String extractRole(String token) {
    return extractClaims(token).get("role", String.class);
  }

  public String extractBranchId(String token) {
    return extractClaims(token).get("branchId", String.class);
  }

  public String extractEmail(String token) {
    return extractClaims(token).get("email", String.class);
  }

  public boolean extractMustChangePassword(String token) {
    return extractClaims(token)
      .get("mustChangePassword", Boolean.class);
  }

  public boolean isTokenExpired(String token) {
    return extractClaims(token)
      .getExpiration()
      .before(new Date());
  }
}
