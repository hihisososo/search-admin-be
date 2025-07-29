package com.yjlee.search.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonbConverterTest {

  private JsonbConverter converter;

  @BeforeEach
  void setUp() {
    converter = new JsonbConverter();
  }

  @Test
  @DisplayName("Map을 JSON 문자열로 변환")
  void should_convert_map_to_json_string() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("age", 25);
    map.put("active", true);

    String json = converter.convertToDatabaseColumn(map);

    assertThat(json).contains("\"name\":\"test\"");
    assertThat(json).contains("\"age\":25");
    assertThat(json).contains("\"active\":true");
  }

  @Test
  @DisplayName("null Map 입력시 null 반환")
  void should_return_null_when_map_is_null() {
    String result = converter.convertToDatabaseColumn(null);
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("빈 Map 입력시 null 반환")
  void should_return_null_when_map_is_empty() {
    String result = converter.convertToDatabaseColumn(new HashMap<>());
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("JSON 문자열을 Map으로 변환")
  void should_convert_json_string_to_map() {
    String json = "{\"name\":\"test\",\"age\":25,\"active\":true}";

    Map<String, Object> map = converter.convertToEntityAttribute(json);

    assertThat(map).hasSize(3);
    assertThat(map.get("name")).isEqualTo("test");
    assertThat(map.get("age")).isEqualTo(25);
    assertThat(map.get("active")).isEqualTo(true);
  }

  @Test
  @DisplayName("null JSON 입력시 빈 Map 반환")
  void should_return_empty_map_when_json_is_null() {
    Map<String, Object> result = converter.convertToEntityAttribute(null);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("빈 JSON 문자열 입력시 빈 Map 반환")
  void should_return_empty_map_when_json_is_empty() {
    Map<String, Object> result = converter.convertToEntityAttribute("");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("공백만 있는 JSON 문자열 입력시 빈 Map 반환")
  void should_return_empty_map_when_json_is_blank() {
    Map<String, Object> result = converter.convertToEntityAttribute("   ");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("잘못된 JSON 형식시 예외 발생")
  void should_throw_exception_when_json_is_invalid() {
    String invalidJson = "{invalid json}";

    assertThatThrownBy(() -> converter.convertToEntityAttribute(invalidJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("JSON 파싱 실패");
  }

  @Test
  @DisplayName("중첩된 객체를 포함한 Map 변환")
  void should_handle_nested_objects() {
    Map<String, Object> nested = new HashMap<>();
    nested.put("city", "Seoul");
    nested.put("country", "Korea");

    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("address", nested);

    String json = converter.convertToDatabaseColumn(map);
    Map<String, Object> result = converter.convertToEntityAttribute(json);

    assertThat(result).hasSize(2);
    assertThat(result.get("name")).isEqualTo("test");
    assertThat(result.get("address")).isInstanceOf(Map.class);

    Map<String, Object> resultAddress = (Map<String, Object>) result.get("address");
    assertThat(resultAddress.get("city")).isEqualTo("Seoul");
    assertThat(resultAddress.get("country")).isEqualTo("Korea");
  }
}
