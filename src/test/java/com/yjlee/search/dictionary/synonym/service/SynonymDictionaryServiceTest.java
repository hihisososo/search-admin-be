package com.yjlee.search.dictionary.synonym.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.ElasticsearchSynonymsClient;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymResponse;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
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
class SynonymDictionaryServiceTest {

  @Mock private SynonymDictionaryRepository repository;
  @Mock private ElasticsearchClient elasticsearchClient;
  @Mock private IndexEnvironmentService indexEnvironmentService;
  @Mock private ElasticsearchSynonymsClient synonymsClient;
  @InjectMocks private SynonymDictionaryService synonymDictionaryService;

  private SynonymDictionary synonymDictionary;
  private SynonymDictionaryCreateRequest createRequest;
  private SynonymDictionaryUpdateRequest updateRequest;
  private IndexEnvironment indexEnvironment;

  @BeforeEach
  void setUp() {
    synonymDictionary = SynonymDictionary.of("노트북,랩탑,laptop", EnvironmentType.DEV);
    synonymDictionary =
        SynonymDictionary.builder()
            .id(1L)
            .keyword("노트북,랩탑,laptop")
            .environmentType(EnvironmentType.DEV)
            .build();

    createRequest = SynonymDictionaryCreateRequest.builder().keyword("핸드폰,휴대폰,mobile").build();

    updateRequest =
        SynonymDictionaryUpdateRequest.builder().keyword("노트북,랩탑,laptop,notebook").build();

    indexEnvironment =
        IndexEnvironment.builder()
            .environmentType(EnvironmentType.DEV)
            .synonymSetName("synonyms-nori-dev")
            .build();
  }

  @Test
  @DisplayName("동의어 사전 생성 성공")
  void createSuccess() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(false);
    when(repository.save(any(SynonymDictionary.class))).thenReturn(synonymDictionary);

    SynonymDictionaryResponse response =
        synonymDictionaryService.create(createRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).save(any(SynonymDictionary.class));
  }

  @Test
  @DisplayName("동의어 사전 생성 실패 - 중복 키워드")
  void createDuplicate() {
    when(repository.existsByKeywordAndEnvironmentType(anyString(), any(EnvironmentType.class)))
        .thenReturn(true);

    assertThatThrownBy(() -> synonymDictionaryService.create(createRequest, EnvironmentType.DEV))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 존재하는 키워드입니다");
  }

  @Test
  @DisplayName("동의어 사전 목록 조회 - 검색어 있음")
  void getListWithSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<SynonymDictionary> page = new PageImpl<>(Arrays.asList(synonymDictionary));

    when(repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
            EnvironmentType.DEV, "노트북", pageable))
        .thenReturn(page);

    PageResponse<SynonymDictionaryListResponse> response =
        synonymDictionaryService.getList(pageable, "노트북", EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository)
        .findByEnvironmentTypeAndKeywordContainingIgnoreCase(EnvironmentType.DEV, "노트북", pageable);
  }

  @Test
  @DisplayName("동의어 사전 목록 조회 - 검색어 없음")
  void getListWithoutSearch() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<SynonymDictionary> page = new PageImpl<>(Arrays.asList(synonymDictionary));

    when(repository.findByEnvironmentType(EnvironmentType.DEV, pageable)).thenReturn(page);

    PageResponse<SynonymDictionaryListResponse> response =
        synonymDictionaryService.getList(pageable, null, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).hasSize(1);
    verify(repository).findByEnvironmentType(EnvironmentType.DEV, pageable);
  }

  @Test
  @DisplayName("동의어 사전 상세 조회 성공")
  void getSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(synonymDictionary));

    SynonymDictionaryResponse response = synonymDictionaryService.get(1L, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(1L);
    verify(repository).findById(1L);
  }

  @Test
  @DisplayName("동의어 사전 상세 조회 실패 - 존재하지 않음")
  void getNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> synonymDictionaryService.get(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("동의어 사전 수정 성공")
  void updateSuccess() {
    when(repository.findById(1L)).thenReturn(Optional.of(synonymDictionary));
    when(repository.save(any(SynonymDictionary.class))).thenReturn(synonymDictionary);

    SynonymDictionaryResponse response =
        synonymDictionaryService.update(1L, updateRequest, EnvironmentType.DEV);

    assertThat(response).isNotNull();
    verify(repository).findById(1L);
    verify(repository).save(any(SynonymDictionary.class));
  }

  @Test
  @DisplayName("동의어 사전 수정 실패 - 존재하지 않음")
  void updateNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> synonymDictionaryService.update(1L, updateRequest, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("동의어 사전 삭제 성공")
  void deleteSuccess() {
    when(repository.existsById(1L)).thenReturn(true);

    assertThatCode(() -> synonymDictionaryService.delete(1L, EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(repository).deleteById(1L);
  }

  @Test
  @DisplayName("동의어 사전 삭제 실패 - 존재하지 않음")
  void deleteNotFound() {
    when(repository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> synonymDictionaryService.delete(1L, EnvironmentType.DEV))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("사전을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("동의어 사전 업로드 성공")
  void uploadSuccess() throws Exception {
    List<SynonymDictionary> dictionaries = Arrays.asList(synonymDictionary);
    PutSynonymResponse mockResponse = mock(PutSynonymResponse.class);

    when(elasticsearchClient.synonyms()).thenReturn(synonymsClient);
    when(synonymsClient.putSynonym(any(PutSynonymRequest.class))).thenReturn(mockResponse);

    assertThatCode(() -> synonymDictionaryService.upload(dictionaries, "v202401011200"))
        .doesNotThrowAnyException();

    verify(synonymsClient).putSynonym(any(PutSynonymRequest.class));
  }

  @Test
  @DisplayName("동의어 사전 업로드 실패")
  void uploadFail() throws Exception {
    List<SynonymDictionary> dictionaries = Arrays.asList(synonymDictionary);

    when(elasticsearchClient.synonyms()).thenReturn(synonymsClient);
    when(synonymsClient.putSynonym(any(PutSynonymRequest.class)))
        .thenThrow(new RuntimeException("ES 연결 실패"));

    assertThatThrownBy(() -> synonymDictionaryService.upload(dictionaries, "v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("동의어 사전 배포 실패");
  }

  @Test
  @DisplayName("환경별 동의어 사전 삭제")
  void deleteByEnvironmentType() {
    assertThatCode(() -> synonymDictionaryService.deleteByEnvironmentType(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경별 동의어 사전 저장 성공")
  void saveToEnvironmentSuccess() {
    List<SynonymDictionary> sourceData = Arrays.asList(synonymDictionary);

    assertThatCode(
            () -> synonymDictionaryService.saveToEnvironment(sourceData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(repository).saveAll(anyList());
  }

  @Test
  @DisplayName("환경별 동의어 사전 저장 - 빈 데이터")
  void saveToEnvironmentEmpty() {
    List<SynonymDictionary> emptyData = Arrays.asList();

    assertThatCode(
            () -> synonymDictionaryService.saveToEnvironment(emptyData, EnvironmentType.PROD))
        .doesNotThrowAnyException();

    verify(repository).deleteByEnvironmentType(EnvironmentType.PROD);
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("동의어 세트 생성 또는 업데이트")
  void createOrUpdateSynonymSet() throws Exception {
    when(repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV))
        .thenReturn(Arrays.asList(synonymDictionary));
    when(indexEnvironmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(indexEnvironment);
    when(elasticsearchClient.synonyms()).thenReturn(synonymsClient);
    when(synonymsClient.putSynonym(any(PutSynonymRequest.class)))
        .thenReturn(mock(PutSynonymResponse.class));

    assertThatCode(() -> synonymDictionaryService.createOrUpdateSynonymSet(EnvironmentType.DEV))
        .doesNotThrowAnyException();

    verify(synonymsClient).putSynonym(any(PutSynonymRequest.class));
  }

  @Test
  @DisplayName("동의어 사전 컨텐츠 조회")
  void getDictionaryContent() {
    when(repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV))
        .thenReturn(Arrays.asList(synonymDictionary));

    String content = synonymDictionaryService.getDictionaryContent(EnvironmentType.DEV);

    assertThat(content).isNotNull();
    assertThat(content).contains("노트북,랩탑,laptop");
  }
}
