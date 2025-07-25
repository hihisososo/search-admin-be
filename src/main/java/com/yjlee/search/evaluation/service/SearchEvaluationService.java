package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.SearchEvaluationRequest;
import com.yjlee.search.evaluation.dto.SearchEvaluationResponse;
import com.yjlee.search.evaluation.model.EvaluationResult;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationResultRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.search.dto.ProductFiltersDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.service.SearchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchEvaluationService {

  private final QueryProductMappingRepository queryProductMappingRepository;
  private final EvaluationResultRepository evaluationResultRepository;
  private final SearchService searchService;

  public SearchEvaluationResponse evaluateSearch(SearchEvaluationRequest request) {
    String query = request.getQuery();
    int topK = request.getTopK();

    log.info("🔍 검색 평가 시작 - 쿼리: '{}', TOP-{}", query, topK);

    // 1. 정답셋 조회
    Optional<QueryProductMapping> groundTruthOpt = queryProductMappingRepository
        .findByQuery(query);
    
    if (groundTruthOpt.isEmpty()) {
      log.warn("⚠️ 정답셋이 없는 쿼리: '{}'", query);
      return buildEmptyResponse(query, topK);
    }

    Set<String> groundTruthIds = parseGroundTruthIds(groundTruthOpt.get().getRelevantProductIds());
    log.debug("📊 정답셋 상품 개수: {}", groundTruthIds.size());

    // 2. ES 검색 수행
    SearchExecuteResponse searchResponse = performSearch(query, topK);
    List<String> searchResultIds = extractProductIds(searchResponse);
    log.debug("🔍 검색 결과 개수: {}", searchResultIds.size());

    // 3. 정확률, 재현률 계산
    Set<String> correctIds = calculateIntersection(searchResultIds, groundTruthIds);
    double precision = calculatePrecision(correctIds.size(), searchResultIds.size());
    double recall = calculateRecall(correctIds.size(), groundTruthIds.size());

    log.info("📈 평가 완료 - 정확률: {:.3f}, 재현률: {:.3f}, 정답: {}/{}", 
        precision, recall, correctIds.size(), groundTruthIds.size());

    return SearchEvaluationResponse.builder()
        .query(query)
        .topK(topK)
        .totalSearchResults(searchResultIds.size())
        .totalGroundTruth(groundTruthIds.size())
        .correctResults(correctIds.size())
        .precision(precision)
        .recall(recall)
        .searchResultIds(searchResultIds)
        .groundTruthIds(groundTruthIds.stream().collect(Collectors.toList()))
        .correctIds(correctIds.stream().collect(Collectors.toList()))
        .build();
  }

  private SearchEvaluationResponse buildEmptyResponse(String query, int topK) {
    return SearchEvaluationResponse.builder()
        .query(query)
        .topK(topK)
        .totalSearchResults(0)
        .totalGroundTruth(0)
        .correctResults(0)
        .precision(0.0)
        .recall(0.0)
        .searchResultIds(List.of())
        .groundTruthIds(List.of())
        .correctIds(List.of())
        .build();
  }

  private Set<String> parseGroundTruthIds(String relevantProductIds) {
    return Arrays.stream(relevantProductIds.split(","))
        .map(String::trim)
        .filter(id -> !id.isEmpty())
        .collect(Collectors.toSet());
  }

  private SearchExecuteResponse performSearch(String query, int topK) {
    SearchExecuteRequest searchRequest = new SearchExecuteRequest();
    searchRequest.setQuery(query);
    searchRequest.setPage(1);
    searchRequest.setSize(topK);
    searchRequest.setApplyTypoCorrection(true);

    // SSD 카테고리 필터 설정
    ProductFiltersDto filters = new ProductFiltersDto();
    filters.setCategory(List.of("SSD"));
    searchRequest.setFilters(filters);

    return searchService.searchProducts(searchRequest);
  }

  private List<String> extractProductIds(SearchExecuteResponse response) {
    if (response.getHits() == null || response.getHits().getData() == null) {
      return List.of();
    }

    return response.getHits().getData().stream()
        .map(product -> product.getId())
        .collect(Collectors.toList());
  }

  private Set<String> calculateIntersection(List<String> searchResults, Set<String> groundTruth) {
    return searchResults.stream()
        .filter(groundTruth::contains)
        .collect(Collectors.toSet());
  }

  private double calculatePrecision(int correctCount, int totalSearchResults) {
    return totalSearchResults > 0 ? (double) correctCount / totalSearchResults : 0.0;
  }

  private double calculateRecall(int correctCount, int totalGroundTruth) {
    return totalGroundTruth > 0 ? (double) correctCount / totalGroundTruth : 0.0;
  }

  public String evaluateAllQueries() {
    log.info("🚀 모든 쿼리 평가 시작");

    List<QueryProductMapping> allMappings = queryProductMappingRepository.findAll();
    log.info("📊 평가 대상 쿼리: {}개", allMappings.size());

    List<EvaluationResult> results = new ArrayList<>();
    int processed = 0;

    for (QueryProductMapping mapping : allMappings) {
      try {
        String query = mapping.getQuery();
        
        SearchEvaluationRequest request = new SearchEvaluationRequest();
        request.setQuery(query);
        request.setTopK(100);
        
        SearchEvaluationResponse response = evaluateSearch(request);
        
        EvaluationResult result = EvaluationResult.builder()
            .query(query)
            .precision(response.getPrecision())
            .recall(response.getRecall())
            .correctCount(response.getCorrectResults())
            .totalSearchResults(response.getTotalSearchResults())
            .totalGroundTruth(response.getTotalGroundTruth())
            .build();
            
        results.add(result);
        processed++;
        
        if (processed % 10 == 0) {
          log.info("📈 진행률: {}/{}", processed, allMappings.size());
        }
        
      } catch (Exception e) {
        log.warn("⚠️ 쿼리 평가 실패: '{}'", mapping.getQuery(), e);
      }
    }

    evaluationResultRepository.saveAll(results);
    log.info("✅ 모든 쿼리 평가 완료: {}개 저장", results.size());
    
    return String.format("평가 완료: %d개 쿼리 처리됨", results.size());
  }
} 