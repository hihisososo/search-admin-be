package com.yjlee.search.search.repository;

import com.yjlee.search.search.model.SearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {

  // 이름으로 검색 (페이징)
  Page<SearchQuery> findByNameContainingIgnoreCase(String name, Pageable pageable);

  // 인덱스명으로 필터링
  Page<SearchQuery> findByIndexNameAndNameContainingIgnoreCase(
      String indexName, String name, Pageable pageable);

  Page<SearchQuery> findByIndexName(String indexName, Pageable pageable);

  // 이름 검색 결과 개수
  long countByNameContainingIgnoreCase(String name);

  // 인덱스별 검색 결과 개수
  long countByIndexNameAndNameContainingIgnoreCase(String indexName, String name);

  long countByIndexName(String indexName);
}
