package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.QuerySuggestResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuerySuggestService {

  private final QueryGenerationService queryGenerationService;
  private final SearchBasedGroundTruthService groundTruthService;

  public QuerySuggestResponse suggestQueries(Integer count, Integer minC, Integer maxC) {
    int target = count != null && count > 0 ? count : 20;
    int minCandidates = minC != null ? minC : 60;
    int maxCandidates = maxC != null ? maxC : 200;

    List<String> generated = queryGenerationService.generateQueriesPreview(target * 3);

    List<String> diversified = deduplicateByTokenJaccard(generated, 0.8);

    List<QuerySuggestResponse.SuggestItem> scored = new ArrayList<>();
    for (String q : diversified) {
      Set<String> ids = groundTruthService.getCandidateIdsForQuery(q);
      int c = ids.size();
      if (c >= minCandidates && c <= maxCandidates) {
        scored.add(QuerySuggestResponse.SuggestItem.builder().query(q).candidateCount(c).build());
      }
      if (scored.size() >= target * 2) {
        break;
      }
    }

    int mid = (minCandidates + maxCandidates) / 2;
    List<QuerySuggestResponse.SuggestItem> top = new ArrayList<>();
    for (QuerySuggestResponse.SuggestItem item :
        scored.stream()
            .sorted(Comparator.comparingInt(i -> Math.abs(i.getCandidateCount() - mid)))
            .collect(Collectors.toList())) {
      if (top.size() >= target) break;
      top.add(item);
    }

    return QuerySuggestResponse.builder()
        .requestedCount(target)
        .returnedCount(top.size())
        .minCandidates(minCandidates)
        .maxCandidates(maxCandidates)
        .items(top)
        .build();
  }

  @SuppressWarnings("unused")
  private List<String> parseJsonArrayLines(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    String cleaned = raw.trim();
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.replaceFirst("```(json)?", "").trim();
      if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
    }
    // 아주 단순 파서: ["a","b"] 형태에서 따옴표 안 문자열만 추출
    List<String> out = new ArrayList<>();
    String[] parts = cleaned.split("\\\\\"\\s*,\\s*\\\\\"");
    for (String p : parts) {
      String s = p.replace("[", "").replace("]", "").replace("\"", "").trim();
      if (!s.isBlank()) out.add(s);
    }
    return out;
  }

  private List<String> deduplicateByTokenJaccard(List<String> list, double threshold) {
    List<String> result = new ArrayList<>();
    List<Set<String>> seen = new ArrayList<>();
    for (String q : list) {
      Set<String> tok = tokenize(q);
      boolean dup = false;
      for (Set<String> s : seen) {
        if (jaccard(tok, s) >= threshold) {
          dup = true;
          break;
        }
      }
      if (!dup) {
        result.add(q);
        seen.add(tok);
      }
    }
    return result;
  }

  private Set<String> tokenize(String q) {
    String[] t = q.toLowerCase().split("[\\n\\r\\t\\s]+");
    return new LinkedHashSet<>(List.of(t));
  }

  private double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() && b.isEmpty()) return 1.0;
    Set<String> inter = new LinkedHashSet<>(a);
    inter.retainAll(b);
    Set<String> union = new LinkedHashSet<>(a);
    union.addAll(b);
    return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
  }

  @SuppressWarnings("unused")
  private List<String> classifyReasons(String q) {
    List<String> r = new ArrayList<>();
    if (q.matches(".*(\\d{2,3}인치|\\d{2,4}hz|\\d+(gb|tb)|\\d{3,5}mah|1ms|65w|4k|8k).*"))
      r.add("스펙형");
    if (q.matches(".*(케이스|필름|보호필름|충전기|케이블|어댑터|커버|밴드|거치대|도킹|스탠드).*")) r.add("액세서리형");
    if (q.matches(".*[a-z]{1,4}[-]?[0-9]{2,4}.*")) r.add("모델형");
    if (r.isEmpty()) r.add("다양성");
    return r;
  }
}
