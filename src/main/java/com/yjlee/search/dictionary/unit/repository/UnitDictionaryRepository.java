package com.yjlee.search.dictionary.unit.repository;

import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitDictionaryRepository extends JpaRepository<UnitDictionary, Long> {
  Page<UnitDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);
}
