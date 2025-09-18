package com.yjlee.search.dictionary.category.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class CategoryMappingListConverter
    implements AttributeConverter<List<CategoryMapping>, String> {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<CategoryMapping> mappings) {
    if (mappings == null || mappings.isEmpty()) {
      return "[]";
    }
    try {
      return objectMapper.writeValueAsString(mappings);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("카테고리 매핑 json 변경 실패", e);
    }
  }

  @Override
  public List<CategoryMapping> convertToEntityAttribute(String json) {
    if (json == null || json.trim().isEmpty() || "[]".equals(json)) {
      return new ArrayList<>();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<CategoryMapping>>() {});
    } catch (IOException e) {
      throw new RuntimeException("카테고리 매핑 json 변경 실패", e);
    }
  }
}
