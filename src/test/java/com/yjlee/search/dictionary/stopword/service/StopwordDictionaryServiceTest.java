package com.yjlee.search.dictionary.stopword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
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
class StopwordDictionaryServiceTest {

  @Mock private StopwordDictionaryRepository stopwordDictionaryRepository;

  @InjectMocks private StopwordDictionaryService stopwordDictionaryService;

  private StopwordDictionary testDictionary;

  @BeforeEach
  void setUp() {
    testDictionary =
        StopwordDictionary.builder().id(1L).keyword("테스트").description("테스트 불용어").build();
  }

  @Test
  @DisplayName("불용어 사전 생성 - 성공")
  void createStopwordDictionary_Success() {
    StopwordDictionaryCreateRequest request =
        StopwordDictionaryCreateRequest.builder().keyword("새불용어").description("새로운 불용어").build();

    when(stopwordDictionaryRepository.save(any(StopwordDictionary.class)))
        .thenReturn(testDictionary);

    StopwordDictionaryResponse response =
        stopwordDictionaryService.create(request, DictionaryEnvironmentType.CURRENT);

    assertThat(response).isNotNull();
    assertThat(response.getKeyword()).isEqualTo("테스트");
    verify(stopwordDictionaryRepository, times(1)).save(any(StopwordDictionary.class));
  }

  @Test
  @DisplayName("현재 불용어 사전 목록 조회 - 검색어 없음")
  void getStopwordDictionaries_CurrentEnvironment_NoSearch() {
    List<StopwordDictionary> dictionaries = Arrays.asList(testDictionary);
    Page<StopwordDictionary> page = new PageImpl<>(dictionaries, PageRequest.of(0, 10), 1);

    when(stopwordDictionaryRepository.findAll(any(Pageable.class))).thenReturn(page);

    PageResponse<StopwordDictionaryListResponse> response =
        stopwordDictionaryService.getList(1, 10, null, null, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getKeyword()).isEqualTo("테스트");
  }

  @Test
  @DisplayName("현재 불용어 사전 목록 조회 - 검색어 포함")
  void getStopwordDictionaries_CurrentEnvironment_WithSearch() {
    List<StopwordDictionary> dictionaries = Arrays.asList(testDictionary);
    Page<StopwordDictionary> page = new PageImpl<>(dictionaries, PageRequest.of(0, 10), 1);

    when(stopwordDictionaryRepository.findByKeywordContainingIgnoreCase(
            anyString(), any(Pageable.class)))
        .thenReturn(page);

    PageResponse<StopwordDictionaryListResponse> response =
        stopwordDictionaryService.getList(1, 10, null, null, "테스트", null);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(stopwordDictionaryRepository, times(1))
        .findByKeywordContainingIgnoreCase(eq("테스트"), any(Pageable.class));
  }

  @Test
  @DisplayName("불용어 사전 상세 조회 - 성공")
  void getStopwordDictionaryDetail_Success() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.of(testDictionary));

    StopwordDictionaryResponse response =
        stopwordDictionaryService.get(1L, DictionaryEnvironmentType.CURRENT);

    assertThat(response).isNotNull();
    assertThat(response.getKeyword()).isEqualTo("테스트");
  }

  @Test
  @DisplayName("불용어 사전 상세 조회 - 존재하지 않는 ID")
  void getStopwordDictionaryDetail_NotFound() {
    when(stopwordDictionaryRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stopwordDictionaryService.get(999L, DictionaryEnvironmentType.CURRENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는");
  }

  @Test
  @DisplayName("불용어 사전 수정 - 성공")
  void updateStopwordDictionary_Success() {
    StopwordDictionaryUpdateRequest request =
        StopwordDictionaryUpdateRequest.builder().keyword("수정됨").description("수정된 설명").build();

    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.of(testDictionary));
    when(stopwordDictionaryRepository.save(any(StopwordDictionary.class)))
        .thenReturn(testDictionary);

    StopwordDictionaryResponse response =
        stopwordDictionaryService.update(1L, request, DictionaryEnvironmentType.CURRENT);

    assertThat(response).isNotNull();
    verify(stopwordDictionaryRepository, times(1)).save(any(StopwordDictionary.class));
  }

  @Test
  @DisplayName("불용어 사전 삭제 - 성공")
  void deleteStopwordDictionary_Success() {
    when(stopwordDictionaryRepository.findById(1L)).thenReturn(Optional.of(testDictionary));

    stopwordDictionaryService.delete(1L, DictionaryEnvironmentType.CURRENT);

    verify(stopwordDictionaryRepository, times(1)).deleteById(1L);
  }

  @Test
  @DisplayName("개발 환경 배포")
  void deployToDev_Success() {
    List<StopwordDictionary> dictionaries = Arrays.asList(testDictionary);
    when(stopwordDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(
            DictionaryEnvironmentType.CURRENT))
        .thenReturn(dictionaries);

    stopwordDictionaryService.deployToDev("test-version");

    verify(stopwordDictionaryRepository, times(1))
        .deleteByEnvironmentType(DictionaryEnvironmentType.DEV);
    verify(stopwordDictionaryRepository, times(1)).saveAll(anyList());
  }
}
