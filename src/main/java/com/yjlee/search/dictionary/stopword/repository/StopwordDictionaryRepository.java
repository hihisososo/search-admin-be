package com.yjlee.search.dictionary.stopword.repository;

import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopwordDictionaryRepository extends JpaRepository<StopwordDictionary, Long> {

  // 키워드로 검색 (페이징)
  Page<StopwordDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);

  // 키워드 존재 여부 확인
  boolean existsByKeyword(String keyword);

  // 전체 조회 (키워드 오름차순 정렬)
  List<StopwordDictionary> findAllByOrderByKeywordAsc();
}
