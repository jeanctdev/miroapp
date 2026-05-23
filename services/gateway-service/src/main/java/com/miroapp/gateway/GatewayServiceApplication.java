package com.miroapp.gateway;


import com.miroapp.security.jwt.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// =====================================================================
// GATEWAY SERVICE — Punto de entrada del sistema MIRO ERP
// =====================================================================
// Puerto: 8080
//
// Responsabilidades:
//   1. Enrutar requests al microservicio correcto según el path
//      /api/auth/**      → auth-service:8081
//      /api/tenants/**   → tenant-service:8082
//      /api/products/**  → product-service:8083
//      /api/categories/** → product-service:8083
//      /api/stock/**     → inventory-service:8084
//
//   2. Validar JWT en todos los paths excepto los públicos
//      /api/auth/login, /api/tenants/register
//
//   3. Propagar contexto del tenant como headers internos
//      X-Tenant-Slug → "venedog" (nombre del schema en PostgreSQL)
//      X-User-Id     → UUID del usuario autenticado
//      X-User-Role   → "TENANT_ADMIN", "MANAGER", "CASHIER", "VIEWER"
//      X-Branch-Id   → UUID de la sucursal (vacío si es null)
//
//   4. Centralizar CORS para todos los servicios del sistema

@SpringBootApplication
@Import(JwtUtil.class)
public class GatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
