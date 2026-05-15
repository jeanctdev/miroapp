package com.miroapp.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

// =====================================================================
// MessageSourceConfig — configura la fuente de mensajes de Miro
//
// ¿Por qué ResourceBundleMessageSource y no Reloadable?
// → ResourceBundleMessageSource es el estándar para producción
// → Carga los mensajes UNA SOLA VEZ al arrancar
// → Más eficiente — no verifica cambios en disco
// → Reloadable es útil solo en desarrollo — no en producción
//
// ¿Por qué AcceptHeaderLocaleResolver?
// → Lee el header Accept-Language del request HTTP
// → Determina el idioma automáticamente
// → Accept-Language: es → español
// → Accept-Language: en → inglés (cuando lo agreguemos)
// → Sin header → español por defecto (LATAM)
// =====================================================================
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

    // Carga messages.properties de cualquier servicio
    // que incluya common-lib en su classpath
    // Cada servicio tiene su propio messages.properties
    // con sus mensajes específicos
    messageSource.setBasename("messages");

    // UTF-8 → soporta tildes, ñ, caracteres especiales
    // Obligatorio para español
    messageSource.setDefaultEncoding("UTF-8");

    // false → lanza excepción si no encuentra la clave
    // Nos avisa en desarrollo si falta algún mensaje
    // Mejor que retornar el código como mensaje
    messageSource.setUseCodeAsDefaultMessage(false);

    // Caching por defecto en ResourceBundleMessageSource
    // Los mensajes se cargan una vez al arrancar
    // Eficiente en producción
    return messageSource;
  }

  // ─── LOCALE RESOLVER ──────────────────────────────────────────
  // Determina el idioma de cada request
  // Lee el header: Accept-Language: es
  @Bean
  public AcceptHeaderLocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    // Español como idioma por defecto para LATAM
    // Cuando agreguemos inglés → solo agregamos messages_en.properties
    // Sin tocar este código
    resolver.setDefaultLocale(Locale.of("es"));
    return resolver;
  }
}
