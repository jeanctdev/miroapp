package com.miroapp.tenant.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

// =====================================================================
// MessageSourceConfig — configuración de mensajes del tenant-service
// =====================================================================
// Copia idéntica a la de auth-service.
// Cada microservicio MVC tiene la suya propia porque
// AcceptHeaderLocaleResolver pertenece a spring-webmvc y no puede
// estar en common-lib (incompatible con WebFlux del gateway).
// =====================================================================
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource =
      new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(false);
    return messageSource;
  }

  @Bean
  public AcceptHeaderLocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver =
      new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.of("es"));
    return resolver;
  }
}
