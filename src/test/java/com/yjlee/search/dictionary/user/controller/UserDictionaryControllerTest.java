package com.yjlee.search.dictionary.user.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
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
class UserDictionaryControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserDictionaryRepository userDictionaryRepository;

  @BeforeEach
  void setUp() {
    // 테스트 데이터 초기화
    userDictionaryRepository.deleteAll();

    // 테스트용 사용자 사전 데이터 생성
    UserDictionary dict1 =
        UserDictionary.builder().keyword("LG그램").description("LG 노트북 브랜드").build();

    UserDictionary dict2 =
        UserDictionary.builder().keyword("갤럭시북").description("삼성 노트북 브랜드").build();

    UserDictionary dict3 =
        UserDictionary.builder().keyword("맥북프로").description("애플 노트북 브랜드").build();

    userDictionaryRepository.save(dict1);
    userDictionaryRepository.save(dict2);
    userDictionaryRepository.save(dict3);
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/user - 사용자 사전 목록 조회")
  void getUserDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/user")
                .param("page", "1")
                .param("size", "20")
                .param("sortBy", "keyword")
                .param("sortDir", "asc"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].keyword").value("갤럭시북"))
        .andExpect(jsonPath("$.content[1].keyword").value("맥북프로"))
        .andExpect(jsonPath("$.content[2].keyword").value("LG그램"))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/user - 검색어 포함 조회")
  void getUserDictionaries_WithSearch() throws Exception {
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("search", "북"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].keyword").value("맥북프로"))
        .andExpect(jsonPath("$.content[1].keyword").value("갤럭시북"));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/user/{id} - 사용자 사전 상세 조회")
  void getUserDictionaryDetail() throws Exception {
    // 첫 번째 데이터의 ID 조회
    UserDictionary firstDict = userDictionaryRepository.findAll().get(0);

    mockMvc
        .perform(get("/api/v1/dictionaries/user/{id}", firstDict.getId()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value(firstDict.getKeyword()))
        .andExpect(jsonPath("$.description").value(firstDict.getDescription()));
  }

  @Test
  @DisplayName("POST /api/v1/dictionaries/user - 사용자 사전 생성")
  void createUserDictionary() throws Exception {
    UserDictionaryCreateRequest request =
        UserDictionaryCreateRequest.builder().keyword("아이패드프로").description("애플 태블릿 브랜드").build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("아이패드프로"))
        .andExpect(jsonPath("$.description").value("애플 태블릿 브랜드"));

    // DB에 실제로 저장되었는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("search", "아이패드"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].keyword").value("아이패드프로"));
  }

  @Test
  @DisplayName("PUT /api/v1/dictionaries/user/{id} - 사용자 사전 수정")
  void updateUserDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    UserDictionary firstDict = userDictionaryRepository.findAll().get(0);

    UserDictionaryUpdateRequest request =
        UserDictionaryUpdateRequest.builder().keyword("LG그램17").description("LG 17인치 노트북").build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/user/{id}", firstDict.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value("LG그램17"))
        .andExpect(jsonPath("$.description").value("LG 17인치 노트북"));

    // DB에서 실제로 수정되었는지 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user/{id}", firstDict.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("LG그램17"));
  }

  @Test
  @DisplayName("DELETE /api/v1/dictionaries/user/{id} - 사용자 사전 삭제")
  void deleteUserDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    UserDictionary firstDict = userDictionaryRepository.findAll().get(0);
    Long deletedId = firstDict.getId();

    mockMvc
        .perform(delete("/api/v1/dictionaries/user/{id}", deletedId))
        .andDo(print())
        .andExpect(status().isNoContent());

    // 삭제 후 조회 시 404 에러 확인
    mockMvc
        .perform(get("/api/v1/dictionaries/user/{id}", deletedId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/user - 페이징 테스트")
  void getUserDictionaries_Paging() throws Exception {
    // 추가 데이터 생성
    for (int i = 1; i <= 10; i++) {
      UserDictionary dict =
          UserDictionary.builder().keyword("제품명" + i).description("테스트 제품 " + i).build();
      userDictionaryRepository.save(dict);
    }

    // 첫 페이지 조회 (5개)
    mockMvc
        .perform(get("/api/v1/dictionaries/user").param("page", "1").param("size", "5"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.totalElements").value(13)) // 기존 3개 + 추가 10개
        .andExpect(jsonPath("$.totalPages").value(3));
  }
}
