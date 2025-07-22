package com.yjlee.search.dictionary.user.repository;

import com.yjlee.search.dictionary.user.model.UserDictionary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDictionaryRepository extends JpaRepository<UserDictionary, Long> {

  // 키워드로 검색 (페이징)
  Page<UserDictionary> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);

  // 키워드 검색 결과 개수
  long countByKeywordContainingIgnoreCase(String keyword);

  // 전체 조회 (키워드 오름차순 정렬)
  List<UserDictionary> findAllByOrderByKeywordAsc();
}
