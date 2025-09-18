package com.yjlee.search.dictionary.synonym.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SynonymDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private SynonymDictionaryRepository synonymDictionaryRepository;

  private SynonymDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    savedDictionary =
        synonymDictionaryRepository.save(
            SynonymDictionary.of("노트북,랩탑,laptop", EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("동의어 사전 목록 조회")
  void getSynonymDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/synonyms")
                .param("environment", "CURRENT")
                .param("search", "노트북"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("노트북,랩탑,laptop"));
  }

  @Test
  @DisplayName("동의어 사전 상세 조회")
  void getSynonymDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/synonyms/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("노트북,랩탑,laptop"));
  }

  @Test
  @DisplayName("동의어 사전 생성")
  void createSynonymDictionary() throws Exception {
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder().keyword("핸드폰,휴대폰,mobile").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/synonyms")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("핸드폰,휴대폰,mobile"));
  }

  @Test
  @DisplayName("동의어 사전 생성 실패 - 빈 키워드")
  void createSynonymDictionaryFailEmpty() throws Exception {
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder().keyword("").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/synonyms")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("동의어 사전 생성 실패 - 중복 키워드")
  void createSynonymDictionaryFailDuplicate() throws Exception {
    SynonymDictionaryCreateRequest request =
        SynonymDictionaryCreateRequest.builder().keyword("노트북,랩탑,laptop").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/synonyms")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("동의어 사전 수정")
  void updateSynonymDictionary() throws Exception {
    SynonymDictionaryUpdateRequest request =
        SynonymDictionaryUpdateRequest.builder().keyword("노트북,랩탑,laptop,notebook").build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/synonyms/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("노트북,랩탑,laptop,notebook"));
  }

  @Test
  @DisplayName("동의어 사전 삭제")
  void deleteSynonymDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/synonyms/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("동의어 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteSynonymDictionaryNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/synonyms/{id}", 99999L).param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }
}
