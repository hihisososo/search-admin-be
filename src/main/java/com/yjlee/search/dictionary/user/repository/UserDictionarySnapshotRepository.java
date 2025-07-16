package com.yjlee.search.dictionary.user.repository;

import com.yjlee.search.dictionary.user.model.UserDictionarySnapshot;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserDictionarySnapshotRepository
    extends JpaRepository<UserDictionarySnapshot, Long> {

  // 특정 버전의 스냅샷 조회 (페이징)
  Page<UserDictionarySnapshot> findByVersionOrderByKeywordAsc(String version, Pageable pageable);

  // 특정 버전에서 키워드 검색 (페이징)
  Page<UserDictionarySnapshot> findByVersionAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
      String version, String keyword, Pageable pageable);

  // 특정 버전의 스냅샷 개수
  long countByVersion(String version);

  // 특정 버전에서 키워드 검색 결과 개수
  long countByVersionAndKeywordContainingIgnoreCase(String version, String keyword);

  // 모든 버전 목록 조회 (중복 제거)
  @Query("SELECT DISTINCT s.version FROM UserDictionarySnapshot s ORDER BY s.version DESC")
  List<String> findDistinctVersionsOrderByVersionDesc();

  // 특정 버전의 모든 스냅샷 조회 (페이징 없음)
  List<UserDictionarySnapshot> findByVersionOrderByKeywordAsc(String version);

  // 버전 존재 여부 확인
  boolean existsByVersion(String version);
}
