package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.IndexMetadata;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndexMetadataRepository extends JpaRepository<IndexMetadata, String> {

  Optional<IndexMetadata> findByName(String name);

  boolean existsByName(String name);

  @Query("SELECT m FROM IndexMetadata m WHERE " + "(:search IS NULL OR m.name LIKE %:search%)")
  List<IndexMetadata> findByNameContaining(@Param("search") String search);

  @Query(
      "SELECT COUNT(m) FROM IndexMetadata m WHERE " + "(:search IS NULL OR m.name LIKE %:search%)")
  long countByNameContaining(@Param("search") String search);

  // 페이징 지원 메서드들 추가
  Page<IndexMetadata> findByNameContainingIgnoreCase(String name, Pageable pageable);

  long countByNameContainingIgnoreCase(String name);
}
