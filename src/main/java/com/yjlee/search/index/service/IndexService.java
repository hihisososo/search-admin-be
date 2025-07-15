package com.yjlee.search.index.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.index.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

  private final IndexQueryService indexQueryService;
  private final IndexManagementService indexManagementService;

  /** 색인 목록 조회 (페이징, 검색, 정렬) */
  public PageResponse<IndexListResponse> getIndexes(
      int page, int size, String search, String sortBy, String sortDir) {
    return indexQueryService.getIndexes(page, size, search, sortBy, sortDir);
  }

  /** 색인 상세 조회 */
  public IndexResponse getIndexDetail(Long indexId) {
    return indexQueryService.getIndexDetail(indexId);
  }

  /** 색인 추가 (파일 업로드 포함) */
  public IndexResponse createIndex(IndexCreateRequest dto, MultipartFile file) {
    return indexManagementService.createIndex(dto, file);
  }

  /** 색인 업데이트 (파일 업로드 포함) */
  public IndexResponse updateIndex(Long indexId, IndexUpdateRequest dto, MultipartFile file) {
    return indexManagementService.updateIndex(indexId, dto, file);
  }

  /** 색인 삭제 */
  public void deleteIndex(Long indexId) {
    indexManagementService.deleteIndex(indexId);
  }

  /** JSON 파일 다운로드용 Presigned URL 생성 */
  public IndexDownloadResponse generateFileDownloadUrl(Long indexId) {
    return indexManagementService.generateFileDownloadUrl(indexId);
  }

  /** 색인 실행 (JSON 파일 데이터를 ES에 색인) */
  public void runIndex(Long indexId) {
    indexManagementService.runIndex(indexId);
  }

  /** 색인명 중복 체크 */
  public boolean checkIndexNameExists(String name) {
    return indexManagementService.checkIndexNameExists(name);
  }
}
