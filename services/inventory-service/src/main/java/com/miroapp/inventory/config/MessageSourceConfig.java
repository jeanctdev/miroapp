package com.miroapp.inventory.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

// =====================================================================
// MessageSourceConfig — configuración de mensajes del inventory-service
// =====================================================================
// Cada microservicio MVC tiene su propia copia.
// AcceptHeaderLocaleResolver es MVC — no puede ir en common-lib
// porque el gateway usa WebFlux y no tiene jakarta.servlet.
// =====================================================================
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource ms =
      new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setUseCodeAsDefaultMessage(false);
    return ms;
  }

  @Bean
  public AcceptHeaderLocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver =
      new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.of("es"));
    return resolver;
  }


}
