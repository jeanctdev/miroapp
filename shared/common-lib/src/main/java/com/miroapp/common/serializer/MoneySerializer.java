package com.miroapp.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

// =====================================================================
// MoneySerializer — serializa BigDecimal a exactamente 2 decimales
// =====================================================================
// ¿Por qué 2 decimales?
//   SUNAT exige 2 decimales en totales de comprobantes
//   Los soles tienen como mínimo S/ 0.01 (un céntimo)
//   No existe S/ 0.009 en la vida real
//
// ¿Por qué HALF_UP?
//   Es el estándar contable peruano e internacional
//   77.7973 → 77.80 (redondea hacia arriba en .005)
//   77.7943 → 77.79 (redondea hacia abajo)
//   Nunca trunca — nunca pierde céntimos al cliente
//
// ¿Por qué no afecta los cálculos internos?
//   Los cálculos SIEMPRE usan los valores de la BD (4 decimales)
//   Este serializer SOLO actúa al convertir la respuesta a JSON
//   El backend nunca calcula desde el JSON redondeado
//
// Uso: @JsonSerialize(using = MoneySerializer.class)
//      en campos BigDecimal que representen precios o montos
// =====================================================================
public class MoneySerializer extends JsonSerializer<BigDecimal> {

  @Override
  public void serialize(
    BigDecimal value,
    JsonGenerator gen,
    SerializerProvider serializers) throws IOException {

    if (value == null) {
      gen.writeNull();
      return;
    }

    // setScale(2, HALF_UP):
    //   77.7973 → 77.80
    //   77.7943 → 77.79
    //   25.0000 → 25.00
    //   8.5000  → 8.50
    gen.writeNumber(
      value.setScale(2, RoundingMode.HALF_UP)
    );
  }
}