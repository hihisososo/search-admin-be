package com.yjlee.search.search.service.builder.query;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.service.builder.model.ExtractedTerms;
import com.yjlee.search.search.service.builder.model.ProcessedQuery;
import com.yjlee.search.search.service.builder.model.QueryContext;
import com.yjlee.search.search.service.typo.TypoCorrectionCacheService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryProcessor {

  private final TypoCorrectionCacheService typoCorrectionCacheService;

  public ProcessedQuery processQuery(SearchExecuteRequest req, EnvironmentType env) {
    if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
      return ProcessedQuery.of("");
    }

    String preprocessedQuery = TextPreprocessor.preprocess(req.getQuery());

    String correctedQuery = preprocessedQuery;
    if (shouldApplyTypoCorrection(req.getApplyTypoCorrection())) {
      correctedQuery = typoCorrectionCacheService.applyTypoCorrection(preprocessedQuery, env);
      if (!correctedQuery.equals(preprocessedQuery)) {
        log.info("오타교정 적용 - 원본: '{}', 교정: '{}'", preprocessedQuery, correctedQuery);
      }
    }

    return ProcessedQuery.builder()
        .original(req.getQuery())
        .normalized(req.getQuery())
        .corrected(correctedQuery)
        .build();
  }

  public ExtractedTerms extractSpecialTerms(String query) {
    if (query == null || query.trim().isEmpty()) {
      return ExtractedTerms.empty();
    }

    return ExtractedTerms.builder().build();
  }

  public String removeTermsFromQuery(String query, ExtractedTerms terms) {
    if (query == null || query.trim().isEmpty()) {
      return "";
    }

    return query.replaceAll("\\s+", " ").trim();
  }

  private boolean shouldApplyTypoCorrection(Boolean applyTypoCorrection) {
    return Optional.ofNullable(applyTypoCorrection).filter(Boolean::booleanValue).isPresent();
  }

  /** 통합 쿼리 분석 메서드 모든 분석을 한 번에 수행하고 QueryContext를 반환 */
  public QueryContext analyzeQuery(SearchExecuteRequest req, EnvironmentType env) {
    // 빈 쿼리 처리
    if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
      return QueryContext.builder()
          .originalQuery("")
          .processedQuery("")
          .queryWithoutTerms("")
          .isQueryEmptyAfterRemoval(true)
          .applyTypoCorrection(req.getApplyTypoCorrection())
          .build();
    }

    // 1. 쿼리 전처리 (정규화, 오타교정)
    ProcessedQuery processed = processQuery(req, env);
    String processedQuery = processed.getFinalQuery();

    // 2. QueryContext 생성 및 반환
    return QueryContext.builder()
        .originalQuery(req.getQuery())
        .processedQuery(processedQuery)
        .queryWithoutTerms(processedQuery)
        .isQueryEmptyAfterRemoval(false)
        .applyTypoCorrection(req.getApplyTypoCorrection())
        .build();
  }
}
