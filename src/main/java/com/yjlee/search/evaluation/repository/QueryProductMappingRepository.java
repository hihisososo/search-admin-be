package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.QueryProductMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryProductMappingRepository extends JpaRepository<QueryProductMapping, Long> {
  Optional<QueryProductMapping> findByQuery(String query);
} 