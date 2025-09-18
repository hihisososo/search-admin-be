package com.yjlee.search.dictionary.user.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private UserDictionaryRepository userDictionaryRepository;

  private UserDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    savedDictionary =
        userDictionaryRepository.save(UserDictionary.of("테스트키워드", EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("사용자 사전 목록 조회")
  void getUserDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/users")
                .param("environment", "CURRENT")
                .param("search", "테스트"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("테스트키워드"));
  }

  @Test
  @DisplayName("사용자 사전 상세 조회")
  void getUserDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/users/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("테스트키워드"));
  }

  @Test
  @DisplayName("사용자 사전 생성")
  void createUserDictionary() throws Exception {
    UserDictionaryCreateRequest request =
        UserDictionaryCreateRequest.builder().keyword("새로운키워드").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/users")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("새로운키워드"));
  }

  @Test
  @DisplayName("사용자 사전 생성 실패 - 빈 키워드")
  void createUserDictionaryFailEmpty() throws Exception {
    UserDictionaryCreateRequest request = UserDictionaryCreateRequest.builder().keyword("").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/users")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("사용자 사전 수정")
  void updateUserDictionary() throws Exception {
    UserDictionaryUpdateRequest request =
        UserDictionaryUpdateRequest.builder().keyword("수정된키워드").build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/users/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("수정된키워드"));
  }

  @Test
  @DisplayName("사용자 사전 삭제")
  void deleteUserDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/users/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("사용자 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteUserDictionaryNotFound() throws Exception {
    mockMvc
        .perform(delete("/api/v1/dictionaries/users/{id}", 99999L).param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }
}
