package com.yjlee.search.dictionary.typo.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TypoCorrectionDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private TypoCorrectionDictionaryRepository typoCorrectionDictionaryRepository;

  private TypoCorrectionDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    savedDictionary =
        typoCorrectionDictionaryRepository.save(
            TypoCorrectionDictionary.of("삼송", "삼성", EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("오타교정 사전 목록 조회")
  void getTypoCorrectionDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/typos").param("environment", "CURRENT").param("search", "삼송"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("삼송"))
        .andExpect(jsonPath("$.content[0].correctedWord").value("삼성"));
  }

  @Test
  @DisplayName("오타교정 사전 상세 조회")
  void getTypoCorrectionDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/typos/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("삼송"))
        .andExpect(jsonPath("$.correctedWord").value("삼성"));
  }

  @Test
  @DisplayName("오타교정 사전 생성")
  void createTypoCorrectionDictionary() throws Exception {
    TypoCorrectionDictionaryCreateRequest request = new TypoCorrectionDictionaryCreateRequest();
    request.setKeyword("엘쥐");
    request.setCorrectedWord("LG");

    mockMvc
        .perform(
            post("/api/v1/dictionaries/typos")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("엘쥐"))
        .andExpect(jsonPath("$.correctedWord").value("LG"));
  }

  @Test
  @DisplayName("오타교정 사전 생성 실패 - 빈 키워드")
  void createTypoCorrectionDictionaryFailEmpty() throws Exception {
    TypoCorrectionDictionaryCreateRequest request = new TypoCorrectionDictionaryCreateRequest();
    request.setKeyword("");
    request.setCorrectedWord("LG");

    mockMvc
        .perform(
            post("/api/v1/dictionaries/typos")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("오타교정 사전 생성 실패 - 중복 키워드")
  void createTypoCorrectionDictionaryFailDuplicate() throws Exception {
    TypoCorrectionDictionaryCreateRequest request = new TypoCorrectionDictionaryCreateRequest();
    request.setKeyword("삼송");
    request.setCorrectedWord("삼성");

    mockMvc
        .perform(
            post("/api/v1/dictionaries/typos")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("오타교정 사전 수정")
  void updateTypoCorrectionDictionary() throws Exception {
    TypoCorrectionDictionaryUpdateRequest request = new TypoCorrectionDictionaryUpdateRequest();
    request.setKeyword("삼숭");
    request.setCorrectedWord("삼성");

    mockMvc
        .perform(
            put("/api/v1/dictionaries/typos/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("삼숭"))
        .andExpect(jsonPath("$.correctedWord").value("삼성"));
  }

  @Test
  @DisplayName("오타교정 사전 삭제")
  void deleteTypoCorrectionDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/typos/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("오타교정 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteTypoCorrectionDictionaryNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/v1/dictionaries/typos/{id}", 99999L).param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }
}
