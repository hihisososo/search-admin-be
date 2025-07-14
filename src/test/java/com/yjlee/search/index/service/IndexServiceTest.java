package com.yjlee.search.index.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.index.dto.IndexRequest;
import com.yjlee.search.index.repository.FileUploadRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import com.yjlee.search.service.S3FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  @Mock private AdminIndexService adminIndexService;

  @Mock private IndexMetadataRepository metadataRepository;

  @Mock private FileUploadRepository fileUploadRepository;

  @Mock private ElasticsearchClient esClient;

  @Mock private S3FileService s3FileService;

  @InjectMocks private IndexService indexService;

  @Test
  void addIndex_성공() {
    // given
    IndexRequest request =
        IndexRequest.builder()
            .name("test-index")
            .dataSource("db")
            .jdbcUrl("jdbc:postgresql://localhost:5432/test")
            .jdbcUser("user")
            .jdbcPassword("password")
            .jdbcQuery("SELECT * FROM test")
            .build();

    when(adminIndexService.existsById(anyString())).thenReturn(false);
    when(metadataRepository.existsByName(anyString())).thenReturn(false);

    // when
    String result = indexService.addIndex(request, null);

    // then
    assertNotNull(result);
    verify(adminIndexService, times(2)).save(any()); // CREATING -> CREATED 상태 변경으로 2번 호출
  }

  @Test
  void addIndex_중복색인명_실패() {
    // given
    IndexRequest request = IndexRequest.builder().name("duplicate-index").dataSource("db").build();

    when(metadataRepository.existsByName("duplicate-index")).thenReturn(true);

    // when & then
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> indexService.addIndex(request, null));

    assertTrue(exception.getMessage().contains("이미 등록된 색인명입니다"));
  }
}
