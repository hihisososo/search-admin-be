package com.yjlee.search.dictionary.unit.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UnitDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private UnitDictionaryRepository unitDictionaryRepository;

  private UnitDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    savedDictionary =
        unitDictionaryRepository.save(UnitDictionary.of("kg/㎏/킬로그램", EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("단위 사전 목록 조회")
  void getUnitDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/units").param("environment", "CURRENT").param("search", "kg"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("kg/㎏/킬로그램"));
  }

  @Test
  @DisplayName("단위 사전 상세 조회")
  void getUnitDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/units/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("kg/㎏/킬로그램"));
  }

  @Test
  @DisplayName("단위 사전 생성")
  void createUnitDictionary() throws Exception {
    UnitDictionaryCreateRequest request =
        UnitDictionaryCreateRequest.builder().keyword("m/미터/meter").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/units")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("m/미터/meter"));
  }

  @Test
  @DisplayName("단위 사전 생성 실패 - 빈 키워드")
  void createUnitDictionaryFailEmpty() throws Exception {
    UnitDictionaryCreateRequest request = UnitDictionaryCreateRequest.builder().keyword("").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/units")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("단위 사전 수정")
  void updateUnitDictionary() throws Exception {
    UnitDictionaryUpdateRequest request =
        UnitDictionaryUpdateRequest.builder().keyword("kg/㎏/킬로그램/킬로").build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/units/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("kg/㎏/킬로그램/킬로"));
  }

  @Test
  @DisplayName("단위 사전 삭제")
  void deleteUnitDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/units/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("단위 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteUnitDictionaryNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/v1/dictionaries/units/{id}", 99999L).param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }
}
