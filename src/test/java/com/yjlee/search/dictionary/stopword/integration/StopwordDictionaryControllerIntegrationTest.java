package com.yjlee.search.dictionary.stopword.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StopwordDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private StopwordDictionaryRepository stopwordDictionaryRepository;

  private StopwordDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    savedDictionary =
        stopwordDictionaryRepository.save(StopwordDictionary.of("은", EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("불용어 사전 목록 조회")
  void getStopwordDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/stopwords")
                .param("environment", "CURRENT")
                .param("search", "은"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("은"));
  }

  @Test
  @DisplayName("불용어 사전 상세 조회")
  void getStopwordDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/stopwords/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("은"));
  }

  @Test
  @DisplayName("불용어 사전 생성")
  void createStopwordDictionary() throws Exception {
    StopwordDictionaryCreateRequest request =
        StopwordDictionaryCreateRequest.builder().keyword("는").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/stopwords")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("는"));
  }

  @Test
  @DisplayName("불용어 사전 생성 실패 - 빈 키워드")
  void createStopwordDictionaryFailEmpty() throws Exception {
    StopwordDictionaryCreateRequest request =
        StopwordDictionaryCreateRequest.builder().keyword("").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/stopwords")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("불용어 사전 수정")
  void updateStopwordDictionary() throws Exception {
    StopwordDictionaryUpdateRequest request =
        StopwordDictionaryUpdateRequest.builder().keyword("이").build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/stopwords/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("이"));
  }

  @Test
  @DisplayName("불용어 사전 삭제")
  void deleteStopwordDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/stopwords/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("불용어 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteStopwordDictionaryNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/stopwords/{id}", 99999L).param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }
}
