package com.miroapp.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miroapp.common.response.ApiError;
import com.miroapp.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

// =====================================================================
// GatewayExceptionHandler — Manejo centralizado de errores del gateway
// =====================================================================
// ¿Qué es ErrorWebExceptionHandler?
//   Es la interfaz de Spring WebFlux para manejar excepciones
//   que ocurren en el pipeline reactivo. Es el equivalente
//   de @ControllerAdvice en el mundo MVC, pero para WebFlux.
//
// ¿Por qué @Order(-1)?
//   Spring Boot registra su propio DefaultErrorWebExceptionHandler
//   con prioridad 0. Si no ponemos @Order(-1), el handler de Spring
//   actúa primero y devuelve su formato propio.
//   Con @Order(-1) nuestro handler tiene mayor prioridad
//   y actúa antes que el de Spring.
//   Regla: número más bajo = mayor prioridad.
// =====================================================================
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

  // ObjectMapper — serializa ApiResponse<T> a JSON String.
  // Spring Boot lo registra automáticamente como @Bean.
  // Lo inyectamos para convertir el objeto Java a JSON.
  private final ObjectMapper objectMapper;

  // MessageSource — obtiene mensajes de messages.properties.
  private final MessageSource messageSource;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange,
                           Throwable ex) {

    ServerHttpResponse response = exchange.getResponse();
    String path = exchange.getRequest().getURI().getPath();

    // ── Determinar el HttpStatus y el código de error ─────────
    // Según el tipo de excepción, decidimos qué status HTTP
    // y qué código de error devolver al cliente.
    HttpStatus status;
    String messageKey;

    switch (ex) {
      case ResponseStatusException rse -> {
        // Ejemplo: 404 cuando una ruta no existe en el gateway
        //          503 cuando el microservicio no está disponible
        status = HttpStatus.valueOf(rse.getStatusCode().value());
        messageKey = resolveMessageKeyFromStatus(status);
      }
      case java.net.ConnectException connectException -> {
        // ConnectException → no se pudo conectar al microservicio
        // El microservicio no está corriendo o no es accesible
        status = HttpStatus.SERVICE_UNAVAILABLE;
        messageKey = "gateway.error.service.unavailable";
      }
      case java.util.concurrent.TimeoutException timeoutException -> {
        // TimeoutException → el microservicio tardó más del límite
        // configurado en httpclient.response-timeout del yaml
        status = HttpStatus.GATEWAY_TIMEOUT;
        messageKey = "gateway.error.service.timeout";
      }
      default -> {
        // Cualquier otra excepción no controlada → 500
        status = HttpStatus.INTERNAL_SERVER_ERROR;
        messageKey = "gateway.error.internal";
      }
    }

    log.error("Gateway error [{}] en path {}: {}", status.value(), path, ex.getMessage());

    // ── Construir la respuesta en formato ApiResponse<T> ──────
    // si el error vino del gateway o de un microservicio.
    String message = messageSource.getMessage(
      messageKey,
      null,
      LocaleContextHolder.getLocale()
    );

    // ApiError → código en MAYUSCULAS + mensaje del properties
    ApiError apiError = ApiError.builder()
      .code(status.name())       // "SERVICE_UNAVAILABLE", "INTERNAL_SERVER_ERROR"
      .message(message)          // mensaje del messages.properties
      .build();

    // ApiResponse<T> → formato estándar de respuesta de MIRO
    // success: false → indica que fue un error
    // data: null     → no hay datos cuando hay error
    // error: apiError → el error con código y mensaje
    ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
      .success(false)
      .data(null)
      .error(apiError)
      .build();

    // ── Escribir la respuesta reactiva ────────────────────────
    // En WebFlux es diferente porque la respuesta es reactiva.
    // Debemos:
    //   1. Configurar status y headers de la respuesta
    //   2. Serializar el objeto a bytes (JSON)
    //   3. Crear un DataBuffer con esos bytes
    //   4. Escribir el DataBuffer en la respuesta con Mono
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    try {
      // Serializar ApiResponse a JSON String
      String body = objectMapper.writeValueAsString(apiResponse);

      // Convertir el String a bytes en UTF-8
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

      // DataBuffer → el contenedor de bytes del mundo reactivo.
      // En MVC usamos OutputStream. En WebFlux usamos DataBuffer.
      // bufferFactory() → factory del servidor Netty para crear buffers
      DataBuffer buffer = response.bufferFactory().wrap(bytes);

      // writeWith(Mono.just(buffer)) → escribe el buffer una vez
      // y completa la respuesta. El Mono.just() envuelve el buffer
      // en un publisher reactivo que emite un solo elemento.
      return response.writeWith(Mono.just(buffer));

    } catch (JsonProcessingException e) {
      // Si falla la serialización JSON (muy raro)
      // cerramos la respuesta sin body
      log.error("Error serializando respuesta de error: {}",
        e.getMessage());
      return response.setComplete();
    }
  }

  // ─── MÉTODOS PRIVADOS ─────────────────────────────────────────
  private String resolveMessageKeyFromStatus(HttpStatus status) {
    return switch (status) {
      case UNAUTHORIZED        -> "gateway.error.token.unauthorized";
      case SERVICE_UNAVAILABLE -> "gateway.error.service.unavailable";
      case GATEWAY_TIMEOUT     -> "gateway.error.service.timeout";
      case BAD_REQUEST         -> "gateway.error.bad.request";
      default                  -> "gateway.error.internal";
    };
  }
}
