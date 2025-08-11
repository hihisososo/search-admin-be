package com.yjlee.search.dictionary.typo.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationListResponse;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationRequest;
import com.yjlee.search.dictionary.typo.recommendation.model.TypoCorrectionRecommendation;
import com.yjlee.search.dictionary.typo.recommendation.repository.TypoCorrectionRecommendationRepository;
import com.yjlee.search.evaluation.service.LLMService;
import java.util.*;
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
      SearchResponse<com.fasterxml.jackson.databind.JsonNode> searchResponse =
          elasticsearchClient.search(
              s ->
                  s.index(indexName)
                      .size(pageSize)
                      .query(q -> q.matchAll(m -> m))
                      .source(src -> src.filter(f -> f.includes("name")))
                      .scroll(sc -> sc.time("10m")),
              com.fasterxml.jackson.databind.JsonNode.class);

      List<
              co.elastic.clients.elasticsearch.core.search.Hit<
                  com.fasterxml.jackson.databind.JsonNode>>
          hits = searchResponse.hits().hits();
      List<String> productNames =
          hits.stream()
              .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
              .filter(Objects::nonNull)
              .map(src -> src.get("name"))
              .filter(Objects::nonNull)
              .map(com.fasterxml.jackson.databind.JsonNode::asText)
              .filter(s -> s != null && !s.isEmpty())
              .limit(sampleLimit)
              .collect(Collectors.toList());

      // 2) 공백 기준 토큰 수집 및 빈도 집계 (Nori 미사용)
      Map<String, Integer> tokenFrequency = new HashMap<>();
      for (String name : productNames) {
        if (name == null || name.isBlank()) continue;
        for (String raw : name.split("\\s+")) {
          String norm = normalizeToken(raw);
          if (norm == null) continue;
          tokenFrequency.merge(norm, 1, Integer::sum);
        }
      }

      // 3) 허용된 교정어 후보: 글자 포함(한글/영문), 길이>=2, 빈도순 상위
      List<String> allowedCorrections =
          tokenFrequency.entrySet().stream()
              .filter(e -> containsLetter(e.getKey()))
              .filter(e -> e.getKey().length() >= 2)
              .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
              .map(Map.Entry::getKey)
              .limit(1000)
              .collect(Collectors.toList());

      // 4) 프롬프트 구성: 상품명 + 허용 교정어 목록을 함께 전달
      String template = promptTemplateLoader.loadTemplate("typo-recommendation.txt");
      StringBuilder namesBuf = new StringBuilder();
      productNames.forEach(n -> namesBuf.append("- ").append(n).append("\n"));

      Double temperature = request != null ? request.getTemperature() : null;
      Integer desired = request != null ? request.getDesiredRecommendationCount() : null;
      final int nameBatchSize = 20; // 상품명은 20줄씩만 전달
      int createdTotal = 0;

      for (int i = 0; i < productNames.size(); i += nameBatchSize) {
        List<String> nameBatch =
            productNames.subList(i, Math.min(i + nameBatchSize, productNames.size()));
        StringBuilder nb = new StringBuilder();
        nameBatch.forEach(n -> nb.append("- ").append(n).append("\n"));
        String prompt = template.replace("{PRODUCT_NAMES}", nb.toString().trim());

        String llmResp = llmService.callLLMAPI(prompt, temperature);
        List<PairCandidate> candidates = parseCandidates(llmResp);

        // 최소한의 형식만 검증하고 저장
        for (PairCandidate c : candidates) {
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
            TypoCorrectionRecommendation r =
                TypoCorrectionRecommendation.builder().pair(key).reason(c.reason).build();
            r.setRecommendationCount(1);
            recommendationRepository.save(r);
            createdTotal++;
          }
          if (desired != null && desired > 0 && createdTotal >= desired) break;
        }
        if (desired != null && desired > 0 && createdTotal >= desired) break;
      }

    } catch (Exception e) {
      log.error("오타 교정어 추천 생성 실패", e);
      throw new RuntimeException("오타 교정어 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  private List<PairCandidate> parseCandidates(String resp) {
    List<PairCandidate> out = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp);
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
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

  private List<PairCandidate> callOneBatch(List<String> batchTokens, Double temperature) {
    // not used in current flow
    return Collections.emptyList();
  }

  @Transactional(readOnly = true)
  public TypoCorrectionRecommendationListResponse getRecommendations() {
    List<TypoCorrectionRecommendation> list =
        recommendationRepository.findAllByOrderByRecommendationCountDesc();
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

  private String normalizeToken(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.chars().anyMatch(Character::isWhitespace)) return null;
    // 공백/특수문자 제거, 소문자화는 하지 않음(한글 보존)
    String cleaned = s.replaceAll("[^0-9A-Za-z가-힣]", "");
    if (cleaned.isEmpty()) return null;
    return cleaned;
  }

  private boolean containsHangul(String s) {
    if (s == null) return false;
    return s.codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3);
  }

  private boolean containsDigit(String s) {
    if (s == null) return false;
    return s.chars().anyMatch(Character::isDigit);
  }

  private boolean containsLatin(String s) {
    if (s == null) return false;
    return s.codePoints().anyMatch(cp -> (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z'));
  }

  private boolean isMostlyNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    long digits = s.chars().filter(Character::isDigit).count();
    return digits * 2 >= s.length(); // 절반 이상 숫자면 숫자성 오타로 간주해 제외
  }

  private boolean containsLetter(String s) {
    if (s == null) return false;
    return s.codePoints().anyMatch(Character::isLetter);
  }
}
