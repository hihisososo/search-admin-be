package com.yjlee.search.dictionary.synonym.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.GenerateRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.ItemResponse;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.ListResponse;
import com.yjlee.search.dictionary.synonym.recommendation.model.SynonymTermRecommendation;
import com.yjlee.search.dictionary.synonym.recommendation.repository.SynonymTermRecommendationRepository;
import com.yjlee.search.evaluation.service.LLMService;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymTermRecommendationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final SynonymTermRecommendationRepository recommendationRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  @Transactional
  public void generate(GenerateRequest request) {
    int sampleLimit = request != null && request.getSampleSize() != null ? request.getSampleSize() : 1000;
    int topK = request != null && request.getTopKTerms() != null ? request.getTopKTerms() : 200;

    try {
      String indexName = indexEnvironmentRepository
          .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
          .map(IndexEnvironment::getIndexName)
          .orElseThrow(() -> new RuntimeException("DEV 환경을 찾을 수 없습니다"));

      int pageSize = Math.min(1000, sampleLimit);
      SearchResponse<JsonNode> searchResponse = elasticsearchClient.search(
          s -> s.index(indexName).size(pageSize).query(q -> q.matchAll(m -> m))
              .source(src -> src.filter(f -> f.includes("name")))
              .scroll(sc -> sc.time("5m")),
          JsonNode.class);

      List<co.elastic.clients.elasticsearch.core.search.Hit<JsonNode>> hits = searchResponse.hits().hits();

      // 1) 상품명에서 Nori 토큰 수집
      List<String> names = hits.stream()
          .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
          .filter(Objects::nonNull)
          .map(src -> src.get("name"))
          .filter(Objects::nonNull)
          .map(JsonNode::asText)
          .filter(s -> s != null && !s.isBlank())
          .limit(sampleLimit)
          .collect(Collectors.toList());

      Map<String, Integer> termFrequency = new HashMap<>();
      for (String name : names) {
        for (String token : analyzeWithNori(indexName, name)) {
          String normalized = normalizeToken(token);
          if (normalized == null) continue;
          termFrequency.merge(normalized, 1, Integer::sum);
        }
      }

      // 2) 빈도 상위 K개를 후보 term으로 선택
      List<String> candidateTerms = termFrequency.entrySet().stream()
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .limit(topK)
          .map(Map.Entry::getKey)
          .collect(Collectors.toList());

      // 3) LLM 호출 (배치 10~20, 독립 판단) + 후처리
      List<TermSynonymResult> results = callLLMByBatches(candidateTerms);
      List<TermSynonymResult> filtered = results.stream()
          .map(this::applyServerPostProcessing)
          .filter(r -> r != null && r.synonyms != null && !r.synonyms.isEmpty())
          .collect(Collectors.toList());

      // 4) 저장 (중복 증가, baseTerm+synonymTerm 단위)
      saveRecommendations(filtered);

    } catch (Exception e) {
      log.error("유의어(term) 추천 생성 실패", e);
      throw new RuntimeException("유의어(term) 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  @Transactional(readOnly = true)
  public ListResponse list() {
    List<SynonymTermRecommendation> all = recommendationRepository.findAllByOrderByRecommendationCountDesc();
    Map<String, List<SynonymTermRecommendation>> byBase = all.stream()
        .collect(Collectors.groupingBy(SynonymTermRecommendation::getBaseTerm, LinkedHashMap::new, Collectors.toList()));

    List<ItemResponse> items = new ArrayList<>();
    for (Map.Entry<String, List<SynonymTermRecommendation>> e : byBase.entrySet()) {
      String base = e.getKey();
      List<ItemResponse.SynonymItem> synonyms = e.getValue().stream()
          .map(r -> ItemResponse.SynonymItem.builder()
              .term(r.getSynonymTerm())
              .reason(r.getReason())
              .recommendationCount(r.getRecommendationCount())
              .build())
          .collect(Collectors.toList());
      items.add(ItemResponse.builder().baseTerm(base).synonyms(synonyms).build());
    }

    return ListResponse.builder().totalCount(items.size()).items(items).build();
  }

  private List<String> analyzeWithNori(String indexName, String text) throws Exception {
    AnalyzeRequest req = AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_index_analyzer").text(text));
    AnalyzeResponse resp = elasticsearchClient.indices().analyze(req);
    List<String> out = new ArrayList<>();
    for (AnalyzeToken t : resp.tokens()) {
      out.add(t.token());
    }
    return out;
  }

  private String normalizeToken(String token) {
    if (token == null) return null;
    String s = token.trim().toLowerCase(Locale.ROOT);
    if (s.length() < 2) return null;
    if (s.chars().anyMatch(Character::isWhitespace)) return null;
    // 숫자/기호만으로 이루어진 토큰 제외
    boolean hasLetter = s.chars().anyMatch(Character::isLetter);
    boolean hasDigit = s.chars().anyMatch(Character::isDigit);
    boolean hasOther = s.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    if (!hasLetter && (hasDigit || hasOther)) return null;
    return s;
  }

  private List<TermSynonymResult> callLLMByBatches(List<String> terms) {
    List<List<String>> batches = new ArrayList<>();
    for (int i = 0; i < terms.size(); i += 15) {
      batches.add(terms.subList(i, Math.min(i + 15, terms.size())));
    }

    int parallel = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
    ExecutorService executor = Executors.newFixedThreadPool(parallel);
    List<Future<List<TermSynonymResult>>> futures = new ArrayList<>();
    for (List<String> batch : batches) {
      futures.add(executor.submit(() -> callOneBatch(batch)));
    }
    List<TermSynonymResult> out = new ArrayList<>();
    for (Future<List<TermSynonymResult>> f : futures) {
      try { out.addAll(f.get()); } catch (Exception ignore) {}
    }
    executor.shutdown();
    return out;
  }

  private List<TermSynonymResult> callOneBatch(List<String> batch) {
    String template = promptTemplateLoader.loadTemplate("synonym-recommendation.txt");
    String termsBlock = String.join("\n", batch);
    String prompt = template.replace("{TERMS}", termsBlock);
    try {
      String resp = llmService.callLLMAPI(prompt);
      return parseLLMJson(resp);
    } catch (Exception e) {
      log.warn("LLM 배치 실패: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  private List<TermSynonymResult> parseLLMJson(String resp) {
    List<TermSynonymResult> out = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp);
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
          String term = n.path("term").asText("");
          List<Synonym> synonyms = new ArrayList<>();
          if (n.path("synonyms").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode s : n.path("synonyms")) {
              String st = s.path("term").asText("");
              String rs = s.path("reason").asText("");
              if (!st.isBlank()) synonyms.add(new Synonym(st, rs));
            }
          }
          if (!term.isBlank()) out.add(new TermSynonymResult(term, synonyms));
        }
      }
    } catch (Exception e) {
      log.warn("LLM JSON 파싱 실패: {}", e.getMessage());
    }
    return out;
  }

  private TermSynonymResult applyServerPostProcessing(TermSynonymResult input) {
    if (input == null || input.synonyms == null) return null;
    String base = normalizeToken(input.baseTerm);
    if (base == null) return null;

    // 정규화 및 중복 제거
    LinkedHashMap<String, Synonym> unique = new LinkedHashMap<>();
    for (Synonym s : input.synonyms) {
      String norm = normalizeToken(s.term);
      if (norm == null) continue;
      unique.putIfAbsent(norm, new Synonym(norm, s.reason));
    }

    // base와 동일한 토큰은 제외
    unique.remove(base);

    // 그룹 필터: 서로 다른 토큰 2개 미만이면 제외 (base와 후보 합쳐서)
    if (unique.isEmpty()) return null;

    // 저장 상한: 최대 3개
    List<Synonym> limited = unique.values().stream().limit(3).collect(Collectors.toList());
    return new TermSynonymResult(base, limited);
  }

  private void saveRecommendations(List<TermSynonymResult> results) {
    for (TermSynonymResult r : results) {
      for (Synonym s : r.synonyms) {
        String base = r.baseTerm;
        String syn = s.term;
        String reason = s.reason;
        recommendationRepository
            .findByBaseTermAndSynonymTerm(base, syn)
            .ifPresentOrElse(
                exist -> {
                  exist.setRecommendationCount(exist.getRecommendationCount() + 1);
                  recommendationRepository.save(exist);
                },
                () -> {
                  SynonymTermRecommendation entity = SynonymTermRecommendation.builder()
                      .baseTerm(base)
                      .synonymTerm(syn)
                      .reason(reason)
                      .build();
                  entity.setRecommendationCount(1);
                  recommendationRepository.save(entity);
                });
      }
    }
  }

  private record TermSynonymResult(String baseTerm, List<Synonym> synonyms) {}
  private record Synonym(String term, String reason) {}
}


