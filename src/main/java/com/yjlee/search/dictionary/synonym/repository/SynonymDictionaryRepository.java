package com.yjlee.search.dictionary.synonym.repository;

import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynonymDictionaryRepository extends JpaRepository<SynonymDictionary, Long> {

  // 키워드로 검색 (페이징)
  Page<SynonymDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);

  // 키워드 검색 결과 개수
  long countByKeywordContainingIgnoreCase(String keyword);

  // 전체 조회 (키워드 오름차순 정렬)
  List<SynonymDictionary> findAllByOrderByKeywordAsc();

  // 특정 base로 시작하는 규칙 제거/검색
  void deleteByKeywordStartingWithIgnoreCase(String keywordPrefix);
}
