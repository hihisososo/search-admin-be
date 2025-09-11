package com.yjlee.search.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonbConverter 테스트")
class JsonbConverterTest {

  private JsonbConverter jsonbConverter;

  @BeforeEach
  void setUp() {
    jsonbConverter = new JsonbConverter();
  }

  @Test
  @DisplayName("Map을 JSON 문자열로 변환")
  void testConvertToDatabaseColumn() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("name", "테스트");
    map.put("age", 25);
    map.put("active", true);

    // when
    String json = jsonbConverter.convertToDatabaseColumn(map);

    // then
    assertThat(json).isNotNull();
    assertThat(json).contains("\"name\":\"테스트\"");
    assertThat(json).contains("\"age\":25");
    assertThat(json).contains("\"active\":true");
  }

  @Test
  @DisplayName("중첩된 Map을 JSON 문자열로 변환")
  void testConvertNestedMapToDatabaseColumn() {
    // given
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("city", "Seoul");
    innerMap.put("country", "Korea");

    Map<String, Object> map = new HashMap<>();
    map.put("name", "테스트");
    map.put("address", innerMap);

    // when
    String json = jsonbConverter.convertToDatabaseColumn(map);

    // then
    assertThat(json).isNotNull();
    assertThat(json).contains("\"name\":\"테스트\"");
    assertThat(json).contains("\"address\"");
    assertThat(json).contains("\"city\":\"Seoul\"");
  }

  @Test
  @DisplayName("null Map을 변환하면 null 반환")
  void testConvertNullMapToDatabaseColumn() {
    // when
    String json = jsonbConverter.convertToDatabaseColumn(null);

    // then
    assertThat(json).isNull();
  }

  @Test
  @DisplayName("빈 Map을 변환하면 null 반환")
  void testConvertEmptyMapToDatabaseColumn() {
    // given
    Map<String, Object> emptyMap = new HashMap<>();

    // when
    String json = jsonbConverter.convertToDatabaseColumn(emptyMap);

    // then
    assertThat(json).isNull();
  }

  @Test
  @DisplayName("JSON 문자열을 Map으로 변환")
  void testConvertToEntityAttribute() {
    // given
    String json = "{\"name\":\"테스트\",\"age\":25,\"active\":true}";

    // when
    Map<String, Object> map = jsonbConverter.convertToEntityAttribute(json);

    // then
    assertThat(map).isNotNull();
    assertThat(map).hasSize(3);
    assertThat(map.get("name")).isEqualTo("테스트");
    assertThat(map.get("age")).isEqualTo(25);
    assertThat(map.get("active")).isEqualTo(true);
  }

  @Test
  @DisplayName("중첩된 JSON을 Map으로 변환")
  void testConvertNestedJsonToEntityAttribute() {
    // given
    String json = "{\"name\":\"테스트\",\"address\":{\"city\":\"Seoul\",\"country\":\"Korea\"}}";

    // when
    Map<String, Object> map = jsonbConverter.convertToEntityAttribute(json);

    // then
    assertThat(map).isNotNull();
    assertThat(map.get("name")).isEqualTo("테스트");
    assertThat(map.get("address")).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> address = (Map<String, Object>) map.get("address");
    assertThat(address.get("city")).isEqualTo("Seoul");
    assertThat(address.get("country")).isEqualTo("Korea");
  }

  @Test
  @DisplayName("null JSON을 변환하면 빈 Map 반환")
  void testConvertNullJsonToEntityAttribute() {
    // when
    Map<String, Object> map = jsonbConverter.convertToEntityAttribute(null);

    // then
    assertThat(map).isNotNull();
    assertThat(map).isEmpty();
  }

  @Test
  @DisplayName("빈 문자열을 변환하면 빈 Map 반환")
  void testConvertEmptyStringToEntityAttribute() {
    // when
    Map<String, Object> map = jsonbConverter.convertToEntityAttribute("");

    // then
    assertThat(map).isNotNull();
    assertThat(map).isEmpty();
  }

  @Test
  @DisplayName("공백 문자열을 변환하면 빈 Map 반환")
  void testConvertWhitespaceToEntityAttribute() {
    // when
    Map<String, Object> map = jsonbConverter.convertToEntityAttribute("   ");

    // then
    assertThat(map).isNotNull();
    assertThat(map).isEmpty();
  }

  @Test
  @DisplayName("잘못된 JSON 파싱 시 예외 발생")
  void testConvertInvalidJsonToEntityAttribute() {
    // given
    String invalidJson = "{invalid json}";

    // when & then
    assertThatThrownBy(() -> jsonbConverter.convertToEntityAttribute(invalidJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("JSON 파싱 실패");
  }

  @Test
  @DisplayName("배열 JSON 처리")
  void testConvertArrayJson() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("items", new String[] {"item1", "item2", "item3"});
    map.put("numbers", new int[] {1, 2, 3});

    // when
    String json = jsonbConverter.convertToDatabaseColumn(map);
    Map<String, Object> result = jsonbConverter.convertToEntityAttribute(json);

    // then
    assertThat(result).isNotNull();
    assertThat(result.get("items")).isInstanceOf(java.util.List.class);
    assertThat(result.get("numbers")).isInstanceOf(java.util.List.class);
  }

  @Test
  @DisplayName("특수 문자 포함 JSON 처리")
  void testConvertSpecialCharacters() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("text", "특수문자 \"test\" \n\t\r");
    map.put("unicode", "한글 테스트 😀");

    // when
    String json = jsonbConverter.convertToDatabaseColumn(map);
    Map<String, Object> result = jsonbConverter.convertToEntityAttribute(json);

    // then
    assertThat(result.get("text")).isEqualTo("특수문자 \"test\" \n\t\r");
    assertThat(result.get("unicode")).isEqualTo("한글 테스트 😀");
  }

  @Test
  @DisplayName("양방향 변환 일관성 테스트")
  void testBidirectionalConversion() {
    // given
    Map<String, Object> originalMap = new HashMap<>();
    originalMap.put("string", "문자열");
    originalMap.put("number", 42);
    originalMap.put("decimal", 3.14);
    originalMap.put("boolean", false);
    originalMap.put("nullValue", null);

    // when
    String json = jsonbConverter.convertToDatabaseColumn(originalMap);
    Map<String, Object> resultMap = jsonbConverter.convertToEntityAttribute(json);

    // then
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.get("string")).isEqualTo("문자열");
    assertThat(resultMap.get("number")).isEqualTo(42);
    assertThat(resultMap.get("decimal")).isEqualTo(3.14);
    assertThat(resultMap.get("boolean")).isEqualTo(false);
    assertThat(resultMap.get("nullValue")).isNull();
  }
}
