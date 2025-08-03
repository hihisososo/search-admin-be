package com.yjlee.search.dictionary.typo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TypoCorrectionDictionaryControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TypoCorrectionDictionaryRepository typoCorrectionDictionaryRepository;

  @BeforeEach
  void setUp() {
    // 테스트 데이터 초기화
    typoCorrectionDictionaryRepository.deleteAll();

    // 테스트용 오타교정 사전 데이터 생성
    TypoCorrectionDictionary dict1 =
        TypoCorrectionDictionary.builder()
            .keyword("아이펀")
            .correctedWord("아이폰")
            .description("애플 제품명 오타")
            .build();

    TypoCorrectionDictionary dict2 =
        TypoCorrectionDictionary.builder()
            .keyword("겔럭시")
            .correctedWord("갤럭시")
            .description("삼성 제품명 오타")
            .build();

    TypoCorrectionDictionary dict3 =
        TypoCorrectionDictionary.builder()
            .keyword("컴퓨타")
            .correctedWord("컴퓨터")
            .description("일반 오타")
            .build();

    typoCorrectionDictionaryRepository.save(dict1);
    typoCorrectionDictionaryRepository.save(dict2);
    typoCorrectionDictionaryRepository.save(dict3);
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/typo - 오타교정 사전 목록 조회")
  void getTypoCorrectionDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/typo")
                .param("page", "1")
                .param("size", "20")
                .param("sortBy", "keyword")
                .param("sortDir", "asc"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].keyword").value("겔럭시"))
        .andExpect(jsonPath("$.content[1].keyword").value("아이펀"))
        .andExpect(jsonPath("$.content[2].keyword").value("컴퓨타"))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/typo - 검색어 포함 조회")
  void getTypoCorrectionDictionaries_WithSearch() throws Exception {
    mockMvc
        .perform(get("/api/v1/dictionaries/typo").param("search", "아이"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].keyword").value("아이펀"))
        .andExpect(jsonPath("$.content[0].correctedWord").value("아이폰"));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/typo/{id} - 오타교정 사전 상세 조회")
  void getTypoCorrectionDictionaryDetail() throws Exception {
    // 첫 번째 데이터의 ID 조회
    TypoCorrectionDictionary firstDict = typoCorrectionDictionaryRepository.findAll().get(0);

    mockMvc
        .perform(get("/api/v1/dictionaries/typo/{id}", firstDict.getId()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value(firstDict.getKeyword()))
        .andExpect(jsonPath("$.correctedWord").value(firstDict.getCorrectedWord()))
        .andExpect(jsonPath("$.description").value(firstDict.getDescription()));
  }

  @Test
  @DisplayName("POST /api/v1/dictionaries/typo - 오타교정 사전 생성")
  void createTypoCorrectionDictionary() throws Exception {
    TypoCorrectionDictionaryCreateRequest request = new TypoCorrectionDictionaryCreateRequest();
    request.setKeyword("노트북크");
    request.setCorrectedWord("노트북");
    request.setDescription("노트북 오타");

    mockMvc
        .perform(
            post("/api/v1/dictionaries/typo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("노트북크"))
        .andExpect(jsonPath("$.correctedWord").value("노트북"))
        .andExpect(jsonPath("$.description").value("노트북 오타"));

    // DB에 실제로 저장되었는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/typo").param("search", "노트북크"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].keyword").value("노트북크"));
  }

  @Test
  @DisplayName("PUT /api/v1/dictionaries/typo/{id} - 오타교정 사전 수정")
  void updateTypoCorrectionDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    TypoCorrectionDictionary firstDict = typoCorrectionDictionaryRepository.findAll().get(0);

    TypoCorrectionDictionaryUpdateRequest request = new TypoCorrectionDictionaryUpdateRequest();
    request.setKeyword("아이폰폰");
    request.setCorrectedWord("아이폰");
    request.setDescription("아이폰 중복 오타");

    mockMvc
        .perform(
            put("/api/v1/dictionaries/typo/{id}", firstDict.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value("아이폰폰"))
        .andExpect(jsonPath("$.description").value("아이폰 중복 오타"));

    // DB에서 실제로 수정되었는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/typo/{id}", firstDict.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("아이폰폰"));
  }

  @Test
  @DisplayName("DELETE /api/v1/dictionaries/typo/{id} - 오타교정 사전 삭제")
  void deleteTypoCorrectionDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    TypoCorrectionDictionary firstDict = typoCorrectionDictionaryRepository.findAll().get(0);
    Long deletedId = firstDict.getId();

    mockMvc
        .perform(delete("/api/v1/dictionaries/typo/{id}", deletedId))
        .andDo(print())
        .andExpect(status().isNoContent());

    // 삭제 후 조회 시 404 에러 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/typo/{id}", deletedId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/typo - 페이징 테스트")
  void getTypoCorrectionDictionaries_Paging() throws Exception {
    // 추가 데이터 생성
    for (int i = 1; i <= 10; i++) {
      TypoCorrectionDictionary dict =
          TypoCorrectionDictionary.builder()
              .keyword("오타" + i)
              .correctedWord("정답" + i)
              .description("테스트 오타 " + i)
              .build();
      typoCorrectionDictionaryRepository.save(dict);
    }

    // 첫 페이지 조회 (5개)
    mockMvc
        .perform(get("/api/v1/dictionaries/typo").param("page", "1").param("size", "5"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.totalElements").value(13)) // 기존 3개 + 추가 10개
        .andExpect(jsonPath("$.totalPages").value(3));
  }
}
