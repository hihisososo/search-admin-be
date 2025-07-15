package com.yjlee.search.index.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.index.dto.IndexListResponse;
import com.yjlee.search.index.dto.IndexResponse;
import com.yjlee.search.index.dto.IndexStatsDto;
import com.yjlee.search.index.mapper.IndexMapper;
import com.yjlee.search.index.model.IndexMetadata;
import com.yjlee.search.index.repository.ElasticsearchRepository;
import com.yjlee.search.index.repository.IndexMetadataRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexQueryService {

  private final IndexMetadataRepository metadataRepository;
  private final ElasticsearchRepository elasticsearchRepository;
  private final IndexMapper indexMapper;

  /** 색인 목록 조회 (페이징, 검색, 정렬) */
  @Transactional(readOnly = true)
  public PageResponse<IndexListResponse> getIndexes(
      int page, int size, String search, String sortBy, String sortDir) {
    log.debug(
        "색인 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}",
        page,
        size,
        search,
        sortBy,
        sortDir);

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<IndexMetadata> metadataPage;
    if (search != null && !search.trim().isEmpty()) {
      metadataPage = metadataRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
    } else {
      metadataPage = metadataRepository.findAll(pageable);
    }
    // 벌크 통계 조회를 위해 모든 인덱스명 수집
    List<String> indexNames =
        metadataPage.getContent().stream().map(IndexMetadata::getName).toList();

    // 벌크로 통계 조회
    Map<String, IndexStatsDto> statsMap = elasticsearchRepository.getBulkIndexStats(indexNames);

    return PageResponse.from(
        metadataPage.map(
            metadata -> {
              IndexStatsDto stats =
                  statsMap.getOrDefault(
                      metadata.getName(), IndexStatsDto.builder().docCount(0L).size(0L).build());
              return indexMapper.toIndexListResponse(metadata, stats);
            }));
  }

  /** 색인 상세 조회 */
  @Transactional(readOnly = true)
  public IndexResponse getIndexDetail(Long indexId) {
    IndexMetadata metadata =
        metadataRepository
            .findById(indexId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 색인입니다: " + indexId));
    return convertToIndexResponse(metadata);
  }

  /** 정렬 조건 생성 */
  private Sort createSort(String sortBy, String sortDir) {
    // 기본값 설정
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    // 허용된 정렬 필드 확인
    String[] allowedFields = {"name", "lastIndexedAt", "createdAt", "updatedAt"};
    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
    }

    // 정렬 방향 설정
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  private IndexResponse convertToIndexResponse(IndexMetadata metadata) {
    String indexName = metadata.getName();
    IndexStatsDto stats = elasticsearchRepository.getIndexStats(indexName);

    return indexMapper.toIndexResponse(metadata, stats);
  }
}
