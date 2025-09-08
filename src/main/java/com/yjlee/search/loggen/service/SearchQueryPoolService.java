package com.yjlee.search.loggen.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.service.CategoryService;
import com.yjlee.search.evaluation.service.LLMService;
import com.yjlee.search.loggen.model.SearchQueryPool;
import com.yjlee.search.loggen.repository.SearchQueryPoolRepository;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryPoolService {

  private final SearchQueryPoolRepository repository;
  private final LLMService llmService;
  private final SearchService searchService;
  private final CategoryService categoryService;
  private final ObjectMapper objectMapper;

  @Transactional
  public int generateAndSaveQueries(int queriesPerCategory) {
    // ExecutorService 생성 (3개 쓰레드 고정)
    int threadPoolSize = 3;
    ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

    try {
      // 1. 모든 카테고리 목록 조회
      List<String> categories =
          categoryService
              .listCategoriesForEnvironment(IndexEnvironment.EnvironmentType.PROD, 0)
              .getCategories()
              .stream()
              .map(c -> c.getName())
              .collect(Collectors.toList());

      if (categories.isEmpty()) {
        log.warn("카테고리 목록이 비어있음");
        return 0;
      }

      log.info(
          "{}개 카테고리에서 각 {}개씩 검색어 생성 시작 (병렬 처리: 3개 쓰레드)", categories.size(), queriesPerCategory);

      // 2. 각 카테고리별로 병렬로 검색어 생성
      Map<String, List<String>> allQueries = new ConcurrentHashMap<>();

      // CompletableFuture 리스트 생성
      List<CompletableFuture<Void>> futures =
          categories.stream()
              .map(
                  category ->
                      CompletableFuture.runAsync(
                          () -> {
                            try {
                              // 카테고리별로 20개 검색어 생성 요청
                              String prompt = buildCategoryPrompt(category);
                              String response = llmService.callLLMAPI(prompt);
                              List<String> queries = parseQueriesFromResponse(response);

                              if (!queries.isEmpty()) {
                                allQueries.put(category, queries);
                                log.debug("카테고리 '{}': {}개 검색어 생성됨", category, queries.size());
                              }
                            } catch (Exception e) {
                              log.warn("카테고리 '{}' 검색어 생성 실패: {}", category, e.getMessage());
                            }
                          },
                          executor))
              .collect(Collectors.toList());

      // 모든 작업 완료 대기
      CompletableFuture<Void> allFutures =
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

      // 타임아웃 설정 (5분)
      allFutures.get(5, TimeUnit.MINUTES);

      log.info("{}개 카테고리 처리 완료, 검색어 검증 및 저장 시작", allQueries.size());

      // 3. 수집된 모든 검색어를 병렬로 검증 후 DB에 저장
      List<CompletableFuture<Integer>> saveFutures = new ArrayList<>();

      for (Map.Entry<String, List<String>> entry : allQueries.entrySet()) {
        String category = entry.getKey();
        List<String> queries = entry.getValue();

        CompletableFuture<Integer> saveFuture =
            CompletableFuture.supplyAsync(
                () -> {
                  int saved = 0;
                  for (String query : queries) {
                    if (saveIfValid(query)) {
                      saved++;
                      log.debug("저장 완료: 카테고리='{}', 검색어='{}'", category, query);
                    }
                  }
                  return saved;
                },
                executor);

        saveFutures.add(saveFuture);
      }

      // 모든 저장 작업 완료 대기 및 결과 합산
      int totalSaved =
          saveFutures.stream().map(CompletableFuture::join).mapToInt(Integer::intValue).sum();

      log.info("검색어 풀 생성 완료: {}개 카테고리에서 총 {}개 저장", allQueries.size(), totalSaved);
      return totalSaved;

    } catch (Exception e) {
      log.error("검색어 풀 생성 실패", e);
      return 0;
    } finally {
      // ExecutorService 종료
      executor.shutdown();
      try {
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private boolean saveIfValid(String query) {
    try {
      // 중복 체크
      if (repository.existsByQuery(query)) {
        log.debug("중복 검색어 스킵: {}", query);
        return false;
      }

      // 실제 검색 수행
      SearchExecuteRequest request = new SearchExecuteRequest();
      request.setQuery(query);
      request.setSize(10);

      SearchExecuteResponse response = searchService.searchProducts(request);

      // 결과가 있으면 저장
      if (response.getHits() != null && response.getHits().getTotal() > 0) {
        SearchQueryPool pool =
            SearchQueryPool.builder()
                .query(query)
                .resultCount(response.getHits().getTotal())
                .build();
        repository.save(pool);
        log.debug("검색어 저장: {} (결과: {}개)", query, response.getHits().getTotal());
        return true;
      } else {
        log.debug("검색 결과 없음: {}", query);
        return false;
      }

    } catch (Exception e) {
      log.error("검색어 검증/저장 실패: {}", query, e);
      return false;
    }
  }

  private String buildCategoryPrompt(String category) {
    try {
      // 프롬프트 템플릿 파일 읽기
      ClassPathResource resource = new ClassPathResource("prompts/search-query-generation.txt");
      String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

      // {category} 치환
      return template.replace("{category}", category);
    } catch (Exception e) {
      log.error("프롬프트 템플릿 로드 실패", e);
      // Fallback 프롬프트
      return String.format(
          "'%s' 카테고리의 상품을 검색할 때 사용할 자연스러운 한국어 검색어 20개를 "
              + "JSON 배열로 생성하세요: [\"검색어1\", \"검색어2\", ..., \"검색어20\"]",
          category);
    }
  }

  private List<String> parseQueriesFromResponse(String response) {
    try {
      // JSON 배열 파싱
      return objectMapper.readValue(response.trim(), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      log.error("LLM 응답 파싱 실패: {}", response);
      return Collections.emptyList();
    }
  }

  public long getPoolCount() {
    return repository.count();
  }

  @Transactional(readOnly = true)
  public List<SearchQueryPool> getAllQueries() {
    return repository.findAll();
  }
}
