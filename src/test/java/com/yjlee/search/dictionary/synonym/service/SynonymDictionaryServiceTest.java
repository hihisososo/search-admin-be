package com.yjlee.search.dictionary.synonym.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionarySnapshotRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SynonymDictionaryServiceTest {

  @Mock
  private SynonymDictionaryRepository synonymDictionaryRepository;

  @Mock
  private SynonymDictionarySnapshotRepository snapshotRepository;

  @InjectMocks
  private SynonymDictionaryService synonymDictionaryService;

  private SynonymDictionary testDictionary;

  @BeforeEach
  void setUp() {
    testDictionary = SynonymDictionary.builder()
        .id(1L)
        .keyword("노트북,랩탑")
        .description("동의어")
        .build();
  }

  @Test
  @DisplayName("유의어 사전 생성 - 성공")
  void createSynonymDictionary_Success() {
    SynonymDictionaryCreateRequest request = SynonymDictionaryCreateRequest.builder()
        .keyword("휴대폰,핸드폰")
        .description("동의어")
        .build();

    when(synonymDictionaryRepository.save(any(SynonymDictionary.class)))
        .thenReturn(testDictionary);

    SynonymDictionaryResponse response = synonymDictionaryService.createSynonymDictionary(
        request, DictionaryEnvironmentType.CURRENT);

    assertThat(response).isNotNull();
    assertThat(response.getKeyword()).isEqualTo("노트북,랩탑");
    verify(synonymDictionaryRepository, times(1)).save(any(SynonymDictionary.class));
  }

  @Test
  @DisplayName("유의어 사전 목록 조회 - 검색어 없음")
  void getSynonymDictionaries_NoSearch() {
    List<SynonymDictionary> dictionaries = Arrays.asList(testDictionary);
    Page<SynonymDictionary> page = new PageImpl<>(dictionaries, PageRequest.of(0, 10), 1);

    when(synonymDictionaryRepository.findAll(any(Pageable.class))).thenReturn(page);

    PageResponse<SynonymDictionaryListResponse> response = 
        synonymDictionaryService.getSynonymDictionaries(1, 10, null, null, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getKeyword()).isEqualTo("노트북,랩탑");
  }

  @Test
  @DisplayName("유의어 사전 수정 - 성공")
  void updateSynonymDictionary_Success() {
    SynonymDictionaryUpdateRequest request = SynonymDictionaryUpdateRequest.builder()
        .keyword("노트북,랩탑,컴퓨터")
        .description("수정된 동의어")
        .build();

    when(synonymDictionaryRepository.findById(1L)).thenReturn(Optional.of(testDictionary));
    when(synonymDictionaryRepository.save(any(SynonymDictionary.class))).thenReturn(testDictionary);

    SynonymDictionaryResponse response = synonymDictionaryService.updateSynonymDictionary(
        1L, request, DictionaryEnvironmentType.CURRENT);

    assertThat(response).isNotNull();
    verify(synonymDictionaryRepository, times(1)).save(any(SynonymDictionary.class));
  }

  @Test
  @DisplayName("유의어 사전 삭제 - 성공")
  void deleteSynonymDictionary_Success() {
    when(synonymDictionaryRepository.existsById(1L)).thenReturn(true);

    synonymDictionaryService.deleteSynonymDictionary(1L, DictionaryEnvironmentType.CURRENT);

    verify(synonymDictionaryRepository, times(1)).deleteById(1L);
  }
}