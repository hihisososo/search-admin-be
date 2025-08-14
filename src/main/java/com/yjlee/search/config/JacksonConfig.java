package com.yjlee.search.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  private static final DateTimeFormatter UTC_SECOND_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private static class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      if (value == null) {
        gen.writeNull();
        return;
      }
      ZonedDateTime zoned =
          value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
      String out = zoned.format(UTC_SECOND_FORMATTER);
      gen.writeString(out);
    }
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
    return builder -> {
      JavaTimeModule javaTimeModule = new JavaTimeModule();
      javaTimeModule.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());

      builder.modules(javaTimeModule);
      builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      builder.timeZone(TimeZone.getTimeZone("UTC"));
    };
  }
}
