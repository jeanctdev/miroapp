package com.miroapp.tenant.service;

import com.miroapp.tenant.exception.UserCreationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.SQLException;

// =====================================================================
// UserCreationService — crea el TENANT_ADMIN al registrar una empresa
//
// ¿Por qué está en tenant-service y no en auth-service?
// → Para el MVP evitamos comunicación entre microservicios
// → tenant-service ya tiene acceso al DataSource
// → En el Sprint 3 migramos esto a Feign Client
//
// ¿Qué hace exactamente?
// 1. Genera una contraseña temporal segura con SecureRandom
// 2. La hashea con BCrypt
// 3. Inserta el TENANT_ADMIN en {tenant}.users
// 4. Retorna la contraseña temporal en texto plano
//    (solo se muestra una vez en la respuesta del registro)
// =====================================================================
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCreationService {

  private final DataSource dataSource;

  // BCryptPasswordEncoder → hashea contraseñas de forma segura
  // strength 12 → balance entre seguridad y performance
  // Mayor strength → más seguro pero más lento
  // 10 es el default, 12 es más seguro para producción
  private final BCryptPasswordEncoder passwordEncoder =
    new BCryptPasswordEncoder(12);

  // Caracteres permitidos en la contraseña temporal
  // Mezclamos mayúsculas, minúsculas, números y símbolos
  // Para que sea segura y fácil de tipear
  private static final String CHARS =
    "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";
  // Nota: excluimos caracteres confusos:
  // I, l, 1, O, 0 → difíciles de distinguir visualmente

  // ─── CREAR TENANT ADMIN ───────────────────────────────────────
  // Se llama desde TenantService después de crear el schema
  // Retorna la contraseña temporal en texto plano
  // El caller la incluye en la respuesta al cliente
  public String createTenantAdmin(String tenantSlug,
                                  String adminEmail,
                                  String adminName) {
    log.info("Creando TENANT_ADMIN para schema '{}'", tenantSlug);

    // PASO 1 — Generar contraseña temporal segura
    String temporaryPassword = generateTemporaryPassword();
    log.debug("Contraseña temporal generada para '{}'", adminEmail);

    // PASO 2 — Hashear con BCrypt
    // NUNCA guardamos la contraseña en texto plano
    String hashedPassword = passwordEncoder.encode(temporaryPassword);

    // PASO 3 — Insertar en {tenant}.users
    insertTenantAdmin(
      tenantSlug,
      adminEmail,
      adminName,
      hashedPassword
    );

    log.info("TENANT_ADMIN creado exitosamente en schema '{}'", tenantSlug);

    // PASO 4 — Retornar la contraseña en texto plano
    // Esta es la ÚNICA vez que el texto plano sale del sistema
    // Después solo existe el hash en la BD
    return temporaryPassword;
  }

  // ─── GENERAR CONTRASEÑA TEMPORAL ──────────────────────────────
  // SecureRandom → criptográficamente seguro
  // No usar Random → predecible y no apto para seguridad
  private String generateTemporaryPassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder password = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      password.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return password.toString();
  }

  // ─── INSERTAR EN LA BD ────────────────────────────────────────
  // Usamos JDBC directo porque:
  // → JPA usa el schema por defecto (public)
  // → Necesitamos insertar en el schema del tenant
  // → SET search_path cambia el schema para esta conexión
  private void insertTenantAdmin(
    String tenantSlug,
    String adminEmail,
    String adminName,
    String hashedPassword) {

    // Separamos el nombre completo en first y last name
    String[] nameParts = adminName.trim().split(" ", 2);
    String firstName = nameParts[0];
    String lastName  = nameParts.length > 1 ? nameParts[1] : "";

    String sql = """
            INSERT INTO users (
                id,
                email,
                password_hash,
                first_name,
                last_name,
                role,
                active,
                must_change_password,
                failed_attempts,
                created_at,
                updated_at
            ) VALUES (
                gen_random_uuid(),
                ?,
                ?,
                ?,
                ?,
                'TENANT_ADMIN',
                true,
                true,
                0,
                NOW(),
                NOW()
            )
        """;

    try (var connection = dataSource.getConnection()) {
      // Cambiamos el search_path al schema del tenant
      // Así el INSERT va a {tenant}.users y no a public.users
      connection.createStatement()
        .execute(String.format(
          "SET search_path TO \"%s\"", tenantSlug));

      try (var stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, adminEmail);
        stmt.setString(2, hashedPassword);
        stmt.setString(3, firstName);
        stmt.setString(4, lastName);
        stmt.executeUpdate();
      }

    } catch (SQLException e) {
      log.error("Error creando TENANT_ADMIN en '{}': {}",
        tenantSlug, e.getMessage());
      throw new UserCreationException(tenantSlug, e);
    }
  }
}
