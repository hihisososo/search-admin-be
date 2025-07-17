package com.yjlee.search.searchsimulation.repository;

import com.yjlee.search.searchsimulation.model.SearchSimulationQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchSimulationQueryRepository
    extends JpaRepository<SearchSimulationQuery, Long> {

  // 이름으로 검색 (페이징)
  Page<SearchSimulationQuery> findByNameContainingIgnoreCase(String name, Pageable pageable);

  // 인덱스명으로 필터링
  Page<SearchSimulationQuery> findByIndexNameAndNameContainingIgnoreCase(
      String indexName, String name, Pageable pageable);

  Page<SearchSimulationQuery> findByIndexName(String indexName, Pageable pageable);

  // 이름 검색 결과 개수
  long countByNameContainingIgnoreCase(String name);

  // 인덱스별 검색 결과 개수
  long countByIndexNameAndNameContainingIgnoreCase(String indexName, String name);

  long countByIndexName(String indexName);
}
