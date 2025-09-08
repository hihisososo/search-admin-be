package com.yjlee.search.loggen.repository;

import com.yjlee.search.loggen.model.SearchQueryPool;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchQueryPoolRepository extends JpaRepository<SearchQueryPool, Long> {

  boolean existsByQuery(String query);

  @Query(value = "SELECT * FROM search_query_pool ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
  Optional<SearchQueryPool> findRandomQuery();
}
