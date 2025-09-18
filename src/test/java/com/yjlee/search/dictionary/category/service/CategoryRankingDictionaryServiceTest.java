package com.yjlee.search.dictionary.category.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.index.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CategoryRankingDictionaryServiceTest {

  @Mock private CategoryRankingDictionaryRepository repository;
  @Mock private ProductRepository productRepository;
  @InjectMocks private CategoryRankingDictionaryService categoryRankingDictionaryService;

  private CategoryRankingDictionary categoryRankingDictionary;
  private CategoryRankingDictionaryCreateRequest createRequest;
  private CategoryRankingDictionaryUpdateRequest updateRequest;
  private List<CategoryMappingDto> mappingDtos;

  @BeforeEach
  void setUp() {
    CategoryMapping mapping = CategoryMapping.builder().category("전자제품").weight(1500).build();

    categoryRankingDictionary =
        CategoryRankingDictionary.builder()
            .id(1L)
            .keyword("노트북")
            .categoryMappings(Arrays.asList(mapping))
            .environmentType(EnvironmentType.DEV)
            .build();

    mappingDtos = Arrays.asList(CategoryMappingDto.builder().category("전자제품").weight(1500).build());

    createRequest =
        CategoryRankingDictionaryCreateRequest.builder()
            .keyword("스마트폰")
            .categoryMappings(mappingDtos)
            .build();

    updateRequest =
        CategoryRankingDictionaryUpdateRequest.builder()
            .keyword("노트북PC")
            .categoryMappings(mappingDtos)
            .build();
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 생성 성공")
  void createSuccess() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(false);
    when(repository.save(any(CategoryRankingDictionary.class)))
        .thenReturn(categoryRankingDictionary);

    CategoryRankingDictionaryResponse response =
        categoryRankingDictionaryService.create(createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).save(any(CategoryRankingDictionary.class));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 생성 실패 - 중복 키워드")
  void createDuplicate() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(true);

    assertThatThrownBy(
            () -> categoryRankingDictionaryService.create(createRequest, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 존재하는 키워드입니다");
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 목록 조회 - 검색어 있음")
  void getListWithSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<CategoryRankingDictionary> page = new PageImpl<>(Arrays.asList(categoryRankingDictionary));

    when(repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
            EnvironmentType.DEV, "노트북", pageable))
        .thenReturn(page);

    PageResponse<CategoryRankingDictionaryListResponse> response =
        categoryRankingDictionaryService.getList(pageable, "노트북", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository)
        .findByEnvironmentTypeAndKeywordContainingIgnoreCase(EnvironmentType.DEV, "노트북", pageable);
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 목록 조회 - 검색어 없음")
  void getListWithoutSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<CategoryRankingDictionary> page = new PageImpl<>(Arrays.asList(categoryRankingDictionary));

    when(repository.findByEnvironmentType(EnvironmentType.DEV, pageable)).thenReturn(page);

    PageResponse<CategoryRankingDictionaryListResponse> response =
        categoryRankingDictionaryService.getList(pageable, null, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository).findByEnvironmentType(EnvironmentType.DEV, pageable);
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 상세 조회 성공")
  void getDetailSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(categoryRankingDictionary));

    CategoryRankingDictionaryResponse response = categoryRankingDictionaryService.getDetail(1L);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).findById(1L);
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 상세 조회 실패 - 존재하지 않음")
  void getDetailNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryRankingDictionaryService.getDetail(1L))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 수정 성공")
  void updateSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(categoryRankingDictionary));
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(false);
    when(repository.save(any(CategoryRankingDictionary.class)))
        .thenReturn(categoryRankingDictionary);

    CategoryRankingDictionaryResponse response =
        categoryRankingDictionaryService.update(1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(repository).findById(1L);
    verify(repository).save(any(CategoryRankingDictionary.class));
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> categoryRankingDictionaryService.update(1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 삭제 성공")
  void deleteSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(categoryRankingDictionary));

    assertThatCode(() -> categoryRankingDictionaryService.delete(1L)).doesNotThrowAnyException();

    verify(repository).deleteById(1L);
  }

  @Test
  @DisplayName("카테고리 랭킹 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryRankingDictionaryService.delete(1L))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("카테고리 목록 조회")
  void getCategories() {
    when(productRepository.findDistinctCategoryNames())
        .thenReturn(Arrays.asList("전자제품", "가전", "컴퓨터"));

    CategoryListResponse response =
        categoryRankingDictionaryService.getCategories(EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getTotalCount()).isEqualTo(3);
    assertThat(response.getCategories()).hasSize(3);
    verify(productRepository).findDistinctCategoryNames();
  }

  @Test
  @DisplayName("환경별 카테고리 랭킹 사전 삭제")
  void deleteByEnvironmentType() {
    assertThatCode(
            () -> categoryRankingDictionaryService.deleteByEnvironmentType(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경별 카테고리 랭킹 사전 저장 성공")
  void saveToEnvironmentSuccess() {
    List<CategoryRankingDictionary> sourceData = Arrays.asList(categoryRankingDictionary);

    assertThatCode(
            () ->
                categoryRankingDictionaryService.saveToEnvironment(
                    sourceData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(repository).saveAll(anyList());
  }

  @Test
  @DisplayName("환경별 카테고리 랭킹 사전 저장 - 빈 데이터")
  void saveToEnvironmentEmpty() {
    List<CategoryRankingDictionary> emptyData = Arrays.asList();

    assertThatCode(
            () ->
                categoryRankingDictionaryService.saveToEnvironment(emptyData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(repository, never()).saveAll(anyList());
  }
}
