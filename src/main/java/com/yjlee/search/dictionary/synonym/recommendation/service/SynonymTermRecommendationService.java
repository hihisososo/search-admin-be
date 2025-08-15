package com.yjlee.search.dictionary.synonym.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.GenerateRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.ItemResponse;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymTermRecommendationDtos.ListResponse;
import com.yjlee.search.dictionary.synonym.recommendation.model.SynonymTermRecommendation;
import com.yjlee.search.dictionary.synonym.recommendation.repository.SynonymTermRecommendationRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.evaluation.service.LLMService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
  private final SynonymDictionaryRepository synonymDictionaryRepository;
  private final SynonymDictionaryService synonymDictionaryService;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  @Transactional
  public void generate(GenerateRequest request) {
    int sampleLimit =
        request != null && request.getSampleSize() != null ? request.getSampleSize() : 1000;

    try {
      log.info("[SynonymGen] 시작 - sampleLimit: {}", sampleLimit);
      String indexName =
          indexEnvironmentRepository
              .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
              .map(IndexEnvironment::getIndexName)
              .orElseThrow(() -> new RuntimeException("DEV 환경을 찾을 수 없습니다"));

      int pageSize = Math.min(1000, sampleLimit);
      SearchResponse<JsonNode> searchResponse =
          elasticsearchClient.search(
              s ->
                  s.index(indexName)
                      .size(pageSize)
                      .query(q -> q.matchAll(m -> m))
                      .source(src -> src.filter(f -> f.includes("name")))
                      .scroll(sc -> sc.time("5m")),
              JsonNode.class);

      List<co.elastic.clients.elasticsearch.core.search.Hit<JsonNode>> hits =
          searchResponse.hits().hits();
      log.info("[SynonymGen] ES 조회 완료 - hits: {}", hits.size());

      // 1) 상품명에서 Nori 토큰 수집
      List<String> names =
          hits.stream()
              .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
              .filter(Objects::nonNull)
              .map(src -> src.get("name"))
              .filter(Objects::nonNull)
              .map(JsonNode::asText)
              .filter(s -> s != null && !s.isBlank())
              .limit(sampleLimit)
              .collect(Collectors.toList());
      log.info("[SynonymGen] 상품명 수집 - names: {}", names.size());

      Map<String, Integer> termFrequency = new HashMap<>();
      for (String name : names) {
        for (String token : analyzeWithNori(indexName, name)) {
          String normalized = normalizeToken(token);
          if (normalized == null) continue;
          termFrequency.merge(normalized, 1, Integer::sum);
        }
      }
      log.info("[SynonymGen] 토큰 빈도 수집 완료 - uniqueTerms: {}", termFrequency.size());

      // 2) 모든 term을 빈도순으로 정렬하여 후보로 사용
      List<String> candidateTerms =
          termFrequency.entrySet().stream()
              .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());
      log.info("[SynonymGen] 후보 term 선별 - candidates(all): {}", candidateTerms.size());

      // 3) LLM 호출을 빈도순으로 배치 처리하며 목표 생성 수량 달성 시 조기 종료
      Double temperature = request != null ? request.getTemperature() : null;
      Integer desired = request != null ? request.getDesiredRecommendationCount() : null;
      int createdTotal = 0;
      final int batchSize = 15;
      log.info("[SynonymGen] LLM 순차 처리 시작 - desired: {}", desired);
      for (int i = 0; i < candidateTerms.size(); i += batchSize) {
        List<String> batch =
            candidateTerms.subList(i, Math.min(i + batchSize, candidateTerms.size()));
        List<TermSynonymResult> parsed = callOneBatch(batch, temperature);
        List<TermSynonymResult> filtered =
            parsed.stream()
                .map(this::applyServerPostProcessing)
                .filter(r -> r != null && r.synonyms != null && !r.synonyms.isEmpty())
                .collect(Collectors.toList());
        int created = saveRecommendations(filtered);
        createdTotal += created;
        log.info("[SynonymGen] 배치 저장 - lastCreated: {}, createdTotal: {}", created, createdTotal);
        if (desired != null && desired > 0 && createdTotal >= desired) {
          log.info(
              "[SynonymGen] 목표 달성 조기 종료 - desired: {}, createdTotal: {}", desired, createdTotal);
          break;
        }
      }
      log.info("[SynonymGen] 완료 - createdTotal: {}", createdTotal);

      // 5) 생성된 추천을 유의어 사전에 병합
      mergeRecommendationsIntoDictionary(DictionaryEnvironmentType.CURRENT);

    } catch (Exception e) {
      log.error("유의어(term) 추천 생성 실패", e);
      throw new RuntimeException("유의어(term) 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  @Transactional
  public void mergeToDictionary(DictionaryEnvironmentType environment) {
    log.info("[SynonymGen] 사전 병합 API 호출 - env: {}", environment);
    mergeRecommendationsIntoDictionary(
        environment == null ? DictionaryEnvironmentType.CURRENT : environment);
  }

  @Transactional(readOnly = true)
  public ListResponse list(String sortBy, String sortDir) {
    List<SynonymTermRecommendation> all;
    boolean asc = "asc".equalsIgnoreCase(sortDir);
    if ("base".equalsIgnoreCase(sortBy)) {
      all = recommendationRepository.findAll();
      all.sort(
          (a, b) ->
              asc
                  ? a.getBaseTerm().compareToIgnoreCase(b.getBaseTerm())
                  : b.getBaseTerm().compareToIgnoreCase(a.getBaseTerm()));
    } else if ("synonym".equalsIgnoreCase(sortBy)) {
      all = recommendationRepository.findAll();
      all.sort(
          (a, b) ->
              asc
                  ? a.getSynonymTerm().compareToIgnoreCase(b.getSynonymTerm())
                  : b.getSynonymTerm().compareToIgnoreCase(a.getSynonymTerm()));
    } else {
      all = recommendationRepository.findAllByOrderByRecommendationCountDesc();
      if (asc) {
        Collections.reverse(all);
      }
    }
    Map<String, List<SynonymTermRecommendation>> byBase =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    SynonymTermRecommendation::getBaseTerm,
                    LinkedHashMap::new,
                    Collectors.toList()));

    List<ItemResponse> items = new ArrayList<>();
    for (Map.Entry<String, List<SynonymTermRecommendation>> e : byBase.entrySet()) {
      String base = e.getKey();
      List<ItemResponse.SynonymItem> synonyms =
          e.getValue().stream()
              .map(
                  r ->
                      ItemResponse.SynonymItem.builder()
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
    AnalyzeRequest req =
        AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_index_analyzer").text(text));
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

  // 병렬 일괄 호출 방식은 보류. 필요 시 부하 조건에 맞춰 복원

  private List<TermSynonymResult> callOneBatch(List<String> batch, Double temperature) {
    String template = promptTemplateLoader.loadTemplate("synonym-recommendation.txt");
    String termsBlock = String.join("\n", batch);
    String prompt = template.replace("{TERMS}", termsBlock);
    try {
      log.debug("[SynonymGen] 배치 호출 - size: {}, promptLen: {}", batch.size(), prompt.length());
      String resp = llmService.callLLMAPI(prompt, temperature);
      log.debug("[SynonymGen] 배치 응답 - respLen: {}", resp != null ? resp.length() : -1);
      if (resp != null) {
        log.debug("[SynonymGen] 배치 응답 미리보기: {}", previewOf(resp, 1000));
      }
      List<TermSynonymResult> parsed = parseLLMJson(resp);
      log.debug("[SynonymGen] 배치 파싱 요약: {}", summarizeBatch(parsed, 1000));
      appendSynonymRecommendationsLog(parsed, batch);
      return parsed;
    } catch (Exception e) {
      log.warn("LLM 배치 실패: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  private String previewOf(String text, int maxLen) {
    if (text == null) return "null";
    String oneLine = text.replaceAll("\r?\n", " ").trim();
    return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
  }

  private String summarizeBatch(List<TermSynonymResult> results, int maxLen) {
    if (results == null || results.isEmpty()) return "[]";
    String summary =
        results.stream()
            .map(
                r -> {
                  String syns =
                      r.synonyms == null
                          ? ""
                          : r.synonyms.stream()
                              .map(
                                  s ->
                                      s.term
                                          + (s.reason == null || s.reason.isBlank()
                                              ? ""
                                              : "(" + s.reason + ")"))
                              .collect(Collectors.joining(", "));
                  return r.baseTerm + " -> [" + syns + "]";
                })
            .collect(Collectors.joining("; "));
    return summary.length() <= maxLen ? summary : summary.substring(0, maxLen) + "...";
  }

  private void appendSynonymRecommendationsLog(
      List<TermSynonymResult> results, List<String> batchTerms) {
    try {
      if (results == null) return;
      Path logPath = Path.of("logs", "llm-responses", "llm_synonym_responses.log");
      Files.createDirectories(logPath.getParent());
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      String batchLine = String.join(", ", batchTerms);
      String body =
          results.stream()
              .map(
                  r -> {
                    String syns =
                        r.synonyms == null
                            ? ""
                            : r.synonyms.stream()
                                .map(
                                    s ->
                                        s.term
                                            + (s.reason == null || s.reason.isBlank()
                                                ? ""
                                                : "(" + s.reason + ")"))
                                .collect(Collectors.joining(", "));
                    return r.baseTerm + " -> [" + syns + "]";
                  })
              .collect(Collectors.joining("; "));
      String line = String.format("%s | terms=[%s] | %s%n", timestamp, batchLine, body);
      Files.write(
          logPath,
          line.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    } catch (Exception e) {
      log.warn("[SynonymGen] 파일 로그 기록 실패: {}", e.getMessage());
    }
  }

  private List<TermSynonymResult> parseLLMJson(String resp) {
    List<TermSynonymResult> out = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
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
      String preview = resp == null ? "null" : resp.substring(0, Math.min(200, resp.length()));
      log.warn("LLM JSON 파싱 실패: {}, respPreview: {}", e.getMessage(), preview);
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

  private int saveRecommendations(List<TermSynonymResult> results) {
    AtomicInteger created = new AtomicInteger(0);
    AtomicInteger updated = new AtomicInteger(0);
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
                  updated.incrementAndGet();
                },
                () -> {
                  SynonymTermRecommendation entity =
                      SynonymTermRecommendation.builder()
                          .baseTerm(base)
                          .synonymTerm(syn)
                          .reason(reason)
                          .build();
                  entity.setRecommendationCount(1);
                  recommendationRepository.save(entity);
                  created.incrementAndGet();
                });
      }
    }
    log.info("[SynonymGen] 저장 결과 - created: {}, updated: {}", created.get(), updated.get());
    return created.get();
  }

  private record TermSynonymResult(String baseTerm, List<Synonym> synonyms) {}

  private record Synonym(String term, String reason) {}

  /** 추천 결과를 유의어 사전 포맷으로 병합한다. 예: "삼성,samsung,샘숭" */
  private void mergeRecommendationsIntoDictionary(DictionaryEnvironmentType environment) {
    // 추천 테이블에서 baseTerm별 후보를 모아 사전 룰 문자열을 구성
    List<SynonymTermRecommendation> all =
        recommendationRepository.findAllByOrderByRecommendationCountDesc();
    Map<String, List<SynonymTermRecommendation>> grouped =
        all.stream().collect(Collectors.groupingBy(SynonymTermRecommendation::getBaseTerm));

    // 기존 동일 base로 시작하는 규칙은 일단 삭제 후 재작성 (충돌 방지)
    for (Map.Entry<String, List<SynonymTermRecommendation>> e : grouped.entrySet()) {
      String base = e.getKey();
      List<String> syns =
          e.getValue().stream()
              .sorted(
                  Comparator.comparingInt(SynonymTermRecommendation::getRecommendationCount)
                      .reversed())
              .map(SynonymTermRecommendation::getSynonymTerm)
              .distinct()
              .toList();
      if (syns.isEmpty()) continue;

      String rule =
          java.util.stream.Stream.concat(java.util.stream.Stream.of(base), syns.stream())
              .collect(java.util.stream.Collectors.joining(","));

      // 중복 제거 전략: 동일 base로 시작하는 기존 엔트리를 제거하고 하나로 재생성
      synonymDictionaryRepository.deleteByKeywordStartingWithIgnoreCase(base + ",");
      synonymDictionaryService.createSynonymDictionary(
          SynonymDictionaryCreateRequest.builder().keyword(rule).build(), environment);
      log.info("[SynonymGen] 사전 병합 - {}", rule);
    }
  }
}
