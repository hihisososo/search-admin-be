package com.yjlee.search.index.repository;

import com.yjlee.search.index.model.IndexMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexMetadataRepository extends JpaRepository<IndexMetadata, Long> {

  boolean existsByName(String name);

  Page<IndexMetadata> findByNameContainingIgnoreCase(String name, Pageable pageable);

  long countByNameContainingIgnoreCase(String name);
}
