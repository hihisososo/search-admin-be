package com.yjlee.search.dictionary.typo.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationListResponse;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationRequest;
import com.yjlee.search.dictionary.typo.recommendation.model.TypoCorrectionRecommendation;
import com.yjlee.search.dictionary.typo.recommendation.repository.TypoCorrectionRecommendationRepository;
import com.yjlee.search.evaluation.service.LLMService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionRecommendationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TypoCorrectionRecommendationRepository recommendationRepository;

  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  @Transactional
  public void generateRecommendations(TypoCorrectionRecommendationRequest request) {
    try {
      int sampleLimit =
          request != null && request.getSampleSize() != null && request.getSampleSize() > 0
              ? request.getSampleSize()
              : 1000;

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
                      .scroll(sc -> sc.time("10m")),
              JsonNode.class);

      List<String> productNames = new ArrayList<>();
      final String[] currentScrollIdHolder = new String[] {searchResponse.scrollId()};
      List<Hit<JsonNode>> hits = searchResponse.hits().hits();
      while (hits != null && !hits.isEmpty() && productNames.size() < sampleLimit) {
        for (Hit<JsonNode> h : hits) {
          JsonNode src = h.source();
          if (src != null && src.get("name") != null) {
            String name = src.get("name").asText();
            if (name != null && !name.isEmpty()) {
              productNames.add(name);
              if (productNames.size() >= sampleLimit) break;
            }
          }
        }
        if (productNames.size() >= sampleLimit) break;
        if (currentScrollIdHolder[0] == null || currentScrollIdHolder[0].isEmpty()) break;
        ScrollResponse<JsonNode> scrollResp =
            elasticsearchClient.scroll(
                s -> s.scrollId(currentScrollIdHolder[0]).scroll(sc -> sc.time("2m")),
                JsonNode.class);
        currentScrollIdHolder[0] = scrollResp.scrollId();
        hits = scrollResp.hits().hits();
      }
      if (currentScrollIdHolder[0] != null && !currentScrollIdHolder[0].isEmpty()) {
        try {
          elasticsearchClient.clearScroll(c -> c.scrollId(currentScrollIdHolder[0]));
        } catch (Exception ignore) {
        }
      }

      // 2) LLM 기반 배치 추천 생성 (상품명 20개씩)
      Integer desired = request != null ? request.getDesiredRecommendationCount() : null;
      Double temperature = request != null ? request.getTemperature() : null;
      String template = promptTemplateLoader.loadTemplate("typo-recommendation.txt");
      final int nameBatchSize = 10; // 타임아웃 완화: 배치 크기 축소
      AtomicInteger createdTotal = new AtomicInteger(0);
      AtomicBoolean stop = new AtomicBoolean(false);
      final int totalBatches = (productNames.size() + nameBatchSize - 1) / nameBatchSize;
      AtomicInteger processedBatches = new AtomicInteger(0);

      // 동시성 완화: 타임아웃 빈도 줄이기 위해 병렬도도 완만하게 적용
      ExecutorService executor = Executors.newFixedThreadPool(4);
      List<Future<?>> futures = new ArrayList<>();

      for (int i = 0; i < productNames.size(); i += nameBatchSize) {
        final List<String> nameBatch =
            productNames.subList(i, Math.min(i + nameBatchSize, productNames.size()));
        futures.add(
            executor.submit(
                () -> {
                  if (stop.get()) return;
                  try {
                    StringBuilder nb = new StringBuilder();
                    nameBatch.forEach(n -> nb.append("- ").append(n).append("\n"));
                    String prompt = template.replace("{PRODUCT_NAMES}", nb.toString().trim());
                    String llmResp = llmService.callLLMAPI(prompt, temperature);
                    List<PairCandidate> candidates = parseCandidatesStrict(llmResp);

                    for (PairCandidate c : candidates) {
                      if (stop.get()) break;
                      if (c.pair == null || c.pair.isBlank()) continue;
                      String[] parts = c.pair.split(",", 2);
                      if (parts.length != 2) continue;
                      String typo = parts[0].trim();
                      String corr = parts[1].trim();
                      if (typo.isEmpty() || corr.isEmpty()) continue;
                      if (typo.equalsIgnoreCase(corr)) continue;

                      String key = typo + "," + corr;
                      Optional<TypoCorrectionRecommendation> existing =
                          recommendationRepository.findByPair(key);
                      if (existing.isPresent()) {
                        TypoCorrectionRecommendation r = existing.get();
                        r.setRecommendationCount(r.getRecommendationCount() + 1);
                        recommendationRepository.save(r);
                      } else {
                        if (desired != null && desired > 0 && createdTotal.get() >= desired) {
                          stop.set(true);
                          break;
                        }
                        TypoCorrectionRecommendation r =
                            TypoCorrectionRecommendation.builder()
                                .pair(key)
                                .reason(c.reason)
                                .build();
                        r.setRecommendationCount(1);
                        recommendationRepository.save(r);
                        int cur = createdTotal.incrementAndGet();
                        if (cur % 50 == 0 || (desired != null && desired > 0 && cur >= desired)) {
                          if (desired != null && desired > 0) {
                            log.info("오타 추천 누적 생성: {}개/{}개", cur, desired);
                          } else {
                            log.info("오타 추천 누적 생성: {}개", cur);
                          }
                        }
                        if (desired != null && desired > 0 && cur >= desired) {
                          stop.set(true);
                          break;
                        }
                      }
                    }
                  } catch (Exception e) {
                    log.warn("오타 교정어 배치 처리 실패 - 계속 진행합니다", e);
                  } finally {
                    int done = processedBatches.incrementAndGet();
                    int made = createdTotal.get();
                    if (desired != null && desired > 0) {
                      log.info(
                          "오타 추천 진행률: 배치 {}/{} 처리 완료, 생성 {}개/목표 {}개",
                          done,
                          totalBatches,
                          made,
                          desired);
                    } else {
                      log.info("오타 추천 진행률: 배치 {}/{} 처리 완료, 생성 {}개", done, totalBatches, made);
                    }
                  }
                }));
        if (stop.get()) break;
      }

      for (Future<?> f : futures) {
        try {
          f.get();
        } catch (Exception e) {
          log.warn("추천 작업 대기 중 오류 - 계속 진행합니다", e);
        }
      }
      executor.shutdownNow();

    } catch (Exception e) {
      log.error("오타 교정어 추천 생성 실패", e);
      throw new RuntimeException("오타 교정어 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("unused")
  private List<PairCandidate> parseCandidates(String resp) {
    List<PairCandidate> out = new ArrayList<>();
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(resp);
      if (root.isArray()) {
        for (JsonNode n : root) {
          String typo = n.path("typo").asText("");
          String correction = n.path("correction").asText("");
          String reason = n.path("reason").asText("");
          if (!typo.isBlank() && !correction.isBlank()) {
            out.add(new PairCandidate(typo + "," + correction, reason));
          }
        }
        return out;
      }
    } catch (Exception ignore) {
    }
    for (String line : resp.split("\n")) {
      String s = line.trim();
      if (s.isEmpty()) continue;
      // 라인 포맷 허용: "오타,교정|이유"
      String pair = s;
      String reason = "";
      if (s.contains("|")) {
        String[] parts = s.split("\\|", 2);
        pair = parts[0].trim();
        reason = parts.length > 1 ? parts[1].trim() : "";
      }
      if (!pair.isEmpty()) out.add(new PairCandidate(pair, reason));
    }
    return out;
  }

  // 보다 엄격한 파서: JSON 우선, 실패 시 객체 정규식, 마지막으로 라인 파싱
  private List<PairCandidate> parseCandidatesStrict(String resp) {
    List<PairCandidate> out = new ArrayList<>();
    if (resp == null || resp.isBlank()) return out;
    String payload = resp.trim();
    int l = payload.indexOf('[');
    int r = payload.lastIndexOf(']');
    ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS);
    if (l >= 0 && r > l) {
      String json = payload.substring(l, r + 1);
      try {
        JsonNode root = mapper.readTree(json);
        if (root.isArray()) {
          for (JsonNode n : root) {
            String typo = n.path("typo").asText("");
            String correction = n.path("correction").asText("");
            String reason = n.path("reason").asText("");
            addIfValid(out, typo, correction, reason);
          }
          return out;
        }
      } catch (Exception ignore) {
      }
    }

    // 객체 라인 정규식 추출
    try {
      java.util.regex.Pattern p =
          java.util.regex.Pattern.compile(
              "\\{\\s*\\\"typo\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"correction\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"(?:\\s*,\\s*\\\"reason\\\"\\s*:\\s*\\\"([^\\\"]*)\\\")?[^}]*}");
      java.util.regex.Matcher m = p.matcher(payload);
      boolean any = false;
      while (m.find()) {
        any = true;
        String typo = m.group(1);
        String correction = m.group(2);
        String reason = m.groupCount() >= 3 && m.group(3) != null ? m.group(3) : "";
        addIfValid(out, typo, correction, reason);
      }
      if (any) return out;
    } catch (Exception ignore) {
    }

    // 마지막 라인 파싱: "a,b|reason"
    for (String line : payload.split("\n")) {
      String s = line.trim();
      if (s.isEmpty()) continue;
      if (s.startsWith("{")) continue; // JSON 조각 무시
      String pair = s;
      String reason = "";
      int bar = s.indexOf('|');
      if (bar >= 0) {
        pair = s.substring(0, bar).trim();
        reason = s.substring(bar + 1).trim();
      }
      int comma = pair.indexOf(',');
      if (comma <= 0 || comma >= pair.length() - 1) continue;
      String typo = pair.substring(0, comma).trim();
      String correction = pair.substring(comma + 1).trim();
      addIfValid(out, typo, correction, reason);
    }
    return out;
  }

  private void addIfValid(List<PairCandidate> out, String typo, String correction, String reason) {
    if (typo == null || correction == null) return;
    typo = typo.trim();
    correction = correction.trim();
    if (typo.isEmpty() || correction.isEmpty()) return;
    if (typo.equalsIgnoreCase(correction)) return;
    if (!isSingleToken(typo) || !isSingleToken(correction)) return;
    if (isMixedScript(typo, correction)) return;
    out.add(new PairCandidate(typo + "," + correction, reason == null ? "" : reason));
  }

  private boolean isSingleToken(String s) {
    return s != null && s.matches("[0-9A-Za-z가-힣]+");
  }

  private boolean hasKorean(String s) {
    if (s == null) return false;
    return s.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
  }

  private boolean hasLatin(String s) {
    if (s == null) return false;
    return s.codePoints().anyMatch(cp -> cp <= 0x7F && Character.isAlphabetic(cp));
  }

  private boolean isMixedScript(String a, String b) {
    return (hasKorean(a) && hasLatin(b)) || (hasLatin(a) && hasKorean(b));
  }

  @Transactional(readOnly = true)
  public TypoCorrectionRecommendationListResponse getRecommendations(
      String sortBy, String sortDir) {
    List<TypoCorrectionRecommendation> list;
    boolean asc = "asc".equalsIgnoreCase(sortDir);
    if ("pair".equalsIgnoreCase(sortBy)) {
      list = recommendationRepository.findAll();
      list.sort(
          (a, b) ->
              asc
                  ? a.getPair().compareToIgnoreCase(b.getPair())
                  : b.getPair().compareToIgnoreCase(a.getPair()));
    } else {
      list = recommendationRepository.findAllByOrderByRecommendationCountDesc();
      if (asc) Collections.reverse(list);
    }
    List<TypoCorrectionRecommendationListResponse.TypoCorrectionDetail> details =
        list.stream()
            .map(
                r ->
                    TypoCorrectionRecommendationListResponse.TypoCorrectionDetail.builder()
                        .pair(r.getPair())
                        .reason(r.getReason())
                        .recommendationCount(r.getRecommendationCount())
                        .build())
            .collect(Collectors.toList());
    return TypoCorrectionRecommendationListResponse.builder()
        .totalCount(details.size())
        .recommendations(details)
        .build();
  }

  private record PairCandidate(String pair, String reason) {}

  // helper 제거 (LLM 모드에선 미사용)

}
