package com.yjlee.search.dictionary.synonym.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SynonymDictionaryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SynonymDictionaryRepository synonymDictionaryRepository;

  @BeforeEach
  void setUp() {
    // 테스트 데이터 초기화
    synonymDictionaryRepository.deleteAll();
    
    // 테스트용 유의어 사전 데이터 생성
    SynonymDictionary dict1 = SynonymDictionary.builder()
        .keyword("노트북,랩탑")
        .description("컴퓨터 동의어")
        .build();
    
    SynonymDictionary dict2 = SynonymDictionary.builder()
        .keyword("휴대폰,핸드폰,스마트폰")
        .description("모바일 기기 동의어")
        .build();
    
    SynonymDictionary dict3 = SynonymDictionary.builder()
        .keyword("자동차,차량,승용차")
        .description("교통수단 동의어")
        .build();
    
    synonymDictionaryRepository.save(dict1);
    synonymDictionaryRepository.save(dict2);
    synonymDictionaryRepository.save(dict3);
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/synonym - 유의어 사전 목록 조회")
  void getSynonymDictionaries() throws Exception {
    mockMvc.perform(get("/api/v1/dictionaries/synonym")
            .param("page", "1")
            .param("size", "20")
            .param("sortBy", "keyword")
            .param("sortDir", "asc"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].keyword").value("노트북,랩탑"))
        .andExpect(jsonPath("$.content[1].keyword").value("자동차,차량,승용차"))
        .andExpect(jsonPath("$.content[2].keyword").value("휴대폰,핸드폰,스마트폰"))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/synonym - 검색어 포함 조회")
  void getSynonymDictionaries_WithSearch() throws Exception {
    mockMvc.perform(get("/api/v1/dictionaries/synonym")
            .param("search", "폰"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].keyword").value("휴대폰,핸드폰,스마트폰"));
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/synonym/{id} - 유의어 사전 상세 조회")
  void getSynonymDictionaryDetail() throws Exception {
    // 첫 번째 데이터의 ID 조회
    SynonymDictionary firstDict = synonymDictionaryRepository.findAll().get(0);
    
    mockMvc.perform(get("/api/v1/dictionaries/synonym/{id}", firstDict.getId()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value(firstDict.getKeyword()))
        .andExpect(jsonPath("$.description").value(firstDict.getDescription()));
  }

  @Test
  @DisplayName("POST /api/v1/dictionaries/synonym - 유의어 사전 생성")
  void createSynonymDictionary() throws Exception {
    SynonymDictionaryCreateRequest request = SynonymDictionaryCreateRequest.builder()
        .keyword("TV,텔레비전,티비")
        .description("방송 수신기 동의어")
        .build();

    mockMvc.perform(post("/api/v1/dictionaries/synonym")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("TV,텔레비전,티비"))
        .andExpect(jsonPath("$.description").value("방송 수신기 동의어"));
    
    // DB에 실제로 저장되었는지 확인
    mockMvc.perform(get("/api/v1/dictionaries/synonym")
            .param("search", "TV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].keyword").value("TV,텔레비전,티비"));
  }

  @Test
  @DisplayName("PUT /api/v1/dictionaries/synonym/{id} - 유의어 사전 수정")
  void updateSynonymDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    SynonymDictionary firstDict = synonymDictionaryRepository.findAll().get(0);
    
    SynonymDictionaryUpdateRequest request = SynonymDictionaryUpdateRequest.builder()
        .keyword("노트북,랩탑,컴퓨터")
        .description("컴퓨터 동의어 수정")
        .build();

    mockMvc.perform(put("/api/v1/dictionaries/synonym/{id}", firstDict.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstDict.getId()))
        .andExpect(jsonPath("$.keyword").value("노트북,랩탑,컴퓨터"))
        .andExpect(jsonPath("$.description").value("컴퓨터 동의어 수정"));
    
    // DB에서 실제로 수정되었는지 확인
    mockMvc.perform(get("/api/v1/dictionaries/synonym/{id}", firstDict.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("노트북,랩탑,컴퓨터"));
  }

  @Test
  @DisplayName("DELETE /api/v1/dictionaries/synonym/{id} - 유의어 사전 삭제")
  void deleteSynonymDictionary() throws Exception {
    // 첫 번째 데이터의 ID 조회
    SynonymDictionary firstDict = synonymDictionaryRepository.findAll().get(0);
    Long deletedId = firstDict.getId();
    
    mockMvc.perform(delete("/api/v1/dictionaries/synonym/{id}", deletedId))
        .andDo(print())
        .andExpect(status().isNoContent());
    
    // 삭제 후 조회 시 404 에러 확인
    mockMvc.perform(get("/api/v1/dictionaries/synonym/{id}", deletedId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/dictionaries/synonym - 페이징 테스트")
  void getSynonymDictionaries_Paging() throws Exception {
    // 추가 데이터 생성
    for (int i = 1; i <= 10; i++) {
      SynonymDictionary dict = SynonymDictionary.builder()
          .keyword("동의어" + i)
          .description("테스트 동의어 " + i)
          .build();
      synonymDictionaryRepository.save(dict);
    }
    
    // 첫 페이지 조회 (5개)
    mockMvc.perform(get("/api/v1/dictionaries/synonym")
            .param("page", "1")
            .param("size", "5"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.totalElements").value(13)) // 기존 3개 + 추가 10개
        .andExpect(jsonPath("$.totalPages").value(3));
  }
}