package com.yjlee.search.search.service.builder.query;

import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.util.ModelExtractor;
import com.yjlee.search.index.util.UnitExtractor;
import com.yjlee.search.search.service.builder.model.ExtractedTerms;
import com.yjlee.search.search.service.builder.model.ProcessedQuery;
import com.yjlee.search.search.service.builder.model.QueryContext;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryProcessor {

  private final TypoCorrectionService typoCorrectionService;

  public ProcessedQuery processQuery(String query, Boolean applyTypoCorrection) {
    if (query == null || query.trim().isEmpty()) {
      return ProcessedQuery.of("");
    }

    String normalizedQuery = TextPreprocessor.normalizeUnits(query);
    String preprocessedQuery = TextPreprocessor.preprocess(normalizedQuery);

    String correctedQuery = preprocessedQuery;
    if (shouldApplyTypoCorrection(applyTypoCorrection)) {
      correctedQuery = typoCorrectionService.applyTypoCorrection(preprocessedQuery);
      if (!correctedQuery.equals(preprocessedQuery)) {
        log.info("오타교정 적용 - 원본: '{}', 교정: '{}'", preprocessedQuery, correctedQuery);
      }
    }

    return ProcessedQuery.builder()
        .original(query)
        .normalized(normalizedQuery)
        .corrected(correctedQuery)
        .build();
  }

  public ExtractedTerms extractSpecialTerms(String query) {
    if (query == null || query.trim().isEmpty()) {
      return ExtractedTerms.empty();
    }

    List<String> units = UnitExtractor.extractUnitsForSearch(query);
    List<String> models = ModelExtractor.extractModelsExcludingUnits(query, units);

    return ExtractedTerms.builder().units(units).models(models).build();
  }

  public String removeTermsFromQuery(String query, ExtractedTerms terms) {
    if (query == null || query.trim().isEmpty()) {
      return "";
    }

    String result = query;

    if (terms.hasUnits()) {
      result = removeTerms(result, terms.getUnits());
    }

    if (terms.hasModels()) {
      result = removeTerms(result, terms.getModels());
    }

    return result.replaceAll("\\s+", " ").trim();
  }

  public String removeUnitsFromQuery(String query, List<String> units) {
    if (query == null || query.trim().isEmpty()) {
      return "";
    }
    if (units == null || units.isEmpty()) {
      return query;
    }

    return removeTerms(query, units).replaceAll("\\s+", " ").trim();
  }

  public String removeModelsFromQuery(String query, List<String> models) {
    if (query == null || query.trim().isEmpty()) {
      return "";
    }
    if (models == null || models.isEmpty()) {
      return query;
    }

    return removeTerms(query, models).replaceAll("\\s+", " ").trim();
  }

  private String removeTerms(String query, List<String> terms) {
    String result = query;

    for (String term : terms) {
      if (!term.isEmpty()) {
        result = result.replaceAll("(?i)\\b" + Pattern.quote(term) + "\\b", "");
      }
    }

    return result;
  }

  private boolean shouldApplyTypoCorrection(Boolean applyTypoCorrection) {
    return Optional.ofNullable(applyTypoCorrection).filter(Boolean::booleanValue).isPresent();
  }

  /** 통합 쿼리 분석 메서드 모든 분석을 한 번에 수행하고 QueryContext를 반환 */
  public QueryContext analyzeQuery(String originalQuery, Boolean applyTypoCorrection) {
    // 빈 쿼리 처리
    if (originalQuery == null || originalQuery.trim().isEmpty()) {
      return QueryContext.builder()
          .originalQuery("")
          .processedQuery("")
          .queryWithoutTerms("")
          .isQueryEmptyAfterRemoval(true)
          .applyTypoCorrection(applyTypoCorrection)
          .build();
    }

    // 1. 쿼리 전처리 (정규화, 오타교정)
    ProcessedQuery processed = processQuery(originalQuery, applyTypoCorrection);
    String processedQuery = processed.getFinalQuery();

    // 2. 특수 용어 추출 (단위, 모델명)
    ExtractedTerms extractedTerms = extractSpecialTerms(processedQuery);
    List<String> units = extractedTerms.getUnits();
    List<String> models = extractedTerms.getModels();

    // 3. 단위 확장 (각 단위별로 동의어 그룹 생성)
    Map<String, Set<String>> expandedUnits = new HashMap<>();
    if (units != null && !units.isEmpty()) {
      for (String unit : units) {
        Set<String> expanded = UnitExtractor.expandUnitSynonyms(unit);
        expandedUnits.put(unit, expanded);
      }
    }

    // 4. 단위/모델명 제거한 쿼리 생성
    String queryWithoutTerms = removeTermsFromQuery(processedQuery, extractedTerms);
    boolean isQueryEmptyAfterRemoval = queryWithoutTerms.isEmpty();

    // 5. QueryContext 생성 및 반환
    return QueryContext.builder()
        .originalQuery(originalQuery)
        .processedQuery(processedQuery)
        .units(units)
        .models(models)
        .expandedUnits(expandedUnits)
        .queryWithoutTerms(queryWithoutTerms)
        .isQueryEmptyAfterRemoval(isQueryEmptyAfterRemoval)
        .applyTypoCorrection(applyTypoCorrection)
        .build();
  }
}
