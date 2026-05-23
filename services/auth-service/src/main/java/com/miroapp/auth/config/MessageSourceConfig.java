package com.miroapp.auth.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

// =====================================================================
// MessageSourceConfig — configuración de mensajes del auth-service
// =====================================================================
// ¿Por qué está aquí y no en common-lib?
//
// AcceptHeaderLocaleResolver pertenece a spring-webmvc (MVC).
// Si estuviera en common-lib, el gateway (WebFlux) explotaría
// al arrancar porque spring-webmvc no existe en su classpath.
//
// Cada microservicio MVC tiene su propia copia de esta configuración.
// El gateway tiene su propia versión compatible con WebFlux en
// GatewayConfig.java.
//
// ¿Qué hace?
// Lee el header Accept-Language del request HTTP y determina
// el idioma para los mensajes. Sin header → español por defecto.
// =====================================================================
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

    // Busca messages.properties en el classpath del auth-service.
    // Cada servicio tiene su propio messages.properties con
    // sus mensajes específicos de dominio.
    messageSource.setBasename("messages");

    // UTF-8 obligatorio para tildes, ñ y caracteres especiales
    // en los mensajes en español.
    messageSource.setDefaultEncoding("UTF-8");

    // false → lanza excepción si no encuentra la clave.
    // Útil en desarrollo para detectar claves faltantes.
    messageSource.setUseCodeAsDefaultMessage(false);

    return messageSource;
  }

  @Bean
  public AcceptHeaderLocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver =
      new AcceptHeaderLocaleResolver();

    // Español como idioma por defecto para LATAM.
    // Cuando agreguemos inglés → solo agregamos messages_en.properties
    resolver.setDefaultLocale(Locale.of("es"));
    return resolver;
  }
}
