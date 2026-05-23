package com.miroapp.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

// =====================================================================
// TenantServiceApplication — punto de entrada del microservicio
//
// @SpringBootApplication → activa:
//   → @Configuration    → esta clase puede tener @Bean
//   → @EnableAutoConfiguration → Spring configura todo automáticamente
//   → @ComponentScan    → escanea el package actual y sus hijos
//
// @ComponentScan → le decimos a Spring que busque componentes
//   en DOS packages:
//   → com.miroapp.tenant  → nuestro código del servicio
//   → com.miroapp.common  → GlobalExceptionHandler de common-lib
//
// Sin esto → Spring no encuentra GlobalExceptionHandler
// Con esto → Spring lo registra automáticamente ✅
// =====================================================================
@SpringBootApplication
@ComponentScan(basePackages = {
  "com.miroapp.tenant",   // código del tenant-service
})
public class TenantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantServiceApplication.class, args);
	}

}
