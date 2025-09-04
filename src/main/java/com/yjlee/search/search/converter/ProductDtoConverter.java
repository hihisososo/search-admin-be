package com.yjlee.search.search.converter;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.search.dto.ProductDto;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDtoConverter {

  private final ObjectMapper objectMapper;

  public ProductDto convert(Hit<JsonNode> hit) {
    return convert(hit.id(), hit.score(), hit.source());
  }

  public ProductDto convert(String id, Double score, JsonNode source) {
    if (source == null) {
      return ProductDto.builder().id(id).score(score != null ? score : 0.0).build();
    }

    return ProductDto.builder()
        .id(id)
        .score(score != null ? score : 0.0)
        .name(getTextValue(source, "name"))
        .nameRaw(getTextValue(source, "name_raw", getTextValue(source, "name")))
        .brandName(getTextValue(source, "brand_name"))
        .categoryName(getTextValue(source, "category_name"))
        .price(getIntValue(source, "price"))
        .registeredMonth(getTextValue(source, "registered_month"))
        .rating(getDecimalValue(source, "rating"))
        .reviewCount(getIntValue(source, "review_count"))
        .thumbnailUrl(getTextValue(source, "thumbnail_url"))
        .specs(getTextValue(source, "specs"))
        .specsRaw(getTextValue(source, "specs_raw", getTextValue(source, "specs")))
        .build();
  }

  public ProductDto convertWithExplain(Hit<JsonNode> hit, String explainText) {
    ProductDto dto = convert(hit);
    return dto.toBuilder().explain(explainText).build();
  }

  private String getTextValue(JsonNode node, String field) {
    return getTextValue(node, field, null);
  }

  private String getTextValue(JsonNode node, String field, String defaultValue) {
    if (node.has(field) && !node.get(field).isNull()) {
      return node.get(field).asText();
    }
    return defaultValue;
  }

  private Integer getIntValue(JsonNode node, String field) {
    if (node.has(field) && !node.get(field).isNull()) {
      return node.get(field).asInt();
    }
    return null;
  }

  private BigDecimal getDecimalValue(JsonNode node, String field) {
    if (node.has(field) && !node.get(field).isNull()) {
      String value = node.get(field).asText();
      if (!"null".equals(value) && !value.isEmpty()) {
        try {
          return new BigDecimal(value);
        } catch (NumberFormatException e) {
          log.warn("Failed to parse decimal value for field {}: {}", field, value);
        }
      }
    }
    return null;
  }
}
