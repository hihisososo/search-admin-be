package com.yjlee.search.dictionary.stopword.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StopwordDictionaryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("실제 DB와 연동한 불용어 사전 생성 및 조회")
  void createAndGetStopwordDictionary() throws Exception {
    // 1. 불용어 사전 생성
    StopwordDictionaryCreateRequest request = StopwordDictionaryCreateRequest.builder()
        .keyword("테스트불용어")
        .description("통합테스트용")
        .build();

    mockMvc.perform(post("/api/v1/dictionaries/stopword")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("테스트불용어"));

    // 2. 생성된 불용어 사전 목록 조회
    mockMvc.perform(get("/api/v1/dictionaries/stopword"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].keyword").value("테스트불용어"));
  }
}