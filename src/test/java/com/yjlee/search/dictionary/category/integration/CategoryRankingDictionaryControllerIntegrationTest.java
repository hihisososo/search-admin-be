package com.yjlee.search.dictionary.category.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.CategoryMappingDto;
import com.yjlee.search.dictionary.category.dto.CategoryRankingDictionaryCreateRequest;
import com.yjlee.search.dictionary.category.dto.CategoryRankingDictionaryUpdateRequest;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CategoryRankingDictionaryControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private CategoryRankingDictionaryRepository categoryRankingDictionaryRepository;

  private CategoryRankingDictionary savedDictionary;

  @BeforeEach
  void setUp() {
    List<CategoryMapping> mappings =
        Arrays.asList(CategoryMapping.builder().category("전자제품").weight(1500).build());

    savedDictionary =
        categoryRankingDictionaryRepository.save(
            CategoryRankingDictionary.of("노트북", mappings, EnvironmentType.CURRENT));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 목록 조회")
  void getCategoryRankingDictionaries() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/category-rankings")
                .param("environment", "CURRENT")
                .param("search", "노트북"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].keyword").value("노트북"))
        .andExpect(jsonPath("$.content[0].categoryCount").value(1));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 상세 조회")
  void getCategoryRankingDictionaryDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/category-rankings/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedDictionary.getId()))
        .andExpect(jsonPath("$.keyword").value("노트북"))
        .andExpect(jsonPath("$.categoryMappings[0].category").value("전자제품"))
        .andExpect(jsonPath("$.categoryMappings[0].weight").value(1500));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 생성")
  void createCategoryRankingDictionary() throws Exception {
    List<CategoryMappingDto> mappingDtos =
        Arrays.asList(CategoryMappingDto.builder().category("스마트폰").weight(2000).build());

    CategoryRankingDictionaryCreateRequest request =
        CategoryRankingDictionaryCreateRequest.builder()
            .keyword("아이폰")
            .categoryMappings(mappingDtos)
            .build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/category-rankings")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.keyword").value("아이폰"))
        .andExpect(jsonPath("$.categoryMappings[0].category").value("스마트폰"));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 생성 실패 - 빈 키워드")
  void createCategoryRankingDictionaryFailEmpty() throws Exception {
    List<CategoryMappingDto> mappingDtos =
        Arrays.asList(CategoryMappingDto.builder().category("스마트폰").weight(2000).build());

    CategoryRankingDictionaryCreateRequest request =
        CategoryRankingDictionaryCreateRequest.builder()
            .keyword("")
            .categoryMappings(mappingDtos)
            .build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/category-rankings")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 생성 실패 - 중복 키워드")
  void createCategoryRankingDictionaryFailDuplicate() throws Exception {
    List<CategoryMappingDto> mappingDtos =
        Arrays.asList(CategoryMappingDto.builder().category("전자제품").weight(1500).build());

    CategoryRankingDictionaryCreateRequest request =
        CategoryRankingDictionaryCreateRequest.builder()
            .keyword("노트북")
            .categoryMappings(mappingDtos)
            .build();

    mockMvc
        .perform(
            post("/api/v1/dictionaries/category-rankings")
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 수정")
  void updateCategoryRankingDictionary() throws Exception {
    List<CategoryMappingDto> mappingDtos =
        Arrays.asList(CategoryMappingDto.builder().category("컴퓨터").weight(1800).build());

    CategoryRankingDictionaryUpdateRequest request =
        CategoryRankingDictionaryUpdateRequest.builder()
            .keyword("노트북PC")
            .categoryMappings(mappingDtos)
            .build();

    mockMvc
        .perform(
            put("/api/v1/dictionaries/category-rankings/{id}", savedDictionary.getId())
                .param("environment", "CURRENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keyword").value("노트북PC"))
        .andExpect(jsonPath("$.categoryMappings[0].category").value("컴퓨터"));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 삭제")
  void deleteCategoryRankingDictionary() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/category-rankings/{id}", savedDictionary.getId())
                .param("environment", "CURRENT"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 삭제 실패 - 존재하지 않는 ID")
  void deleteCategoryRankingDictionaryNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/dictionaries/category-rankings/{id}", 99999L)
                .param("environment", "CURRENT"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("카테고리 목록 조회")
  void getCategories() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dictionaries/category-rankings/categories")
                .param("environment", "CURRENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").isNumber())
        .andExpect(jsonPath("$.categories").isArray());
  }
}
