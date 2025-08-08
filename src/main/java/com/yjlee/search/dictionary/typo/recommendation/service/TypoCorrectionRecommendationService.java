package com.yjlee.search.dictionary.typo.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationListResponse;
import com.yjlee.search.dictionary.typo.recommendation.dto.TypoCorrectionRecommendationRequest;
import com.yjlee.search.dictionary.typo.recommendation.model.TypoCorrectionRecommendation;
import com.yjlee.search.dictionary.typo.recommendation.repository.TypoCorrectionRecommendationRepository;
import com.yjlee.search.evaluation.service.LLMService;
import com.yjlee.search.common.util.PromptTemplateLoader;
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
      int maxToProcess = request != null && request.getSampleSize() != null && request.getSampleSize() > 0
          ? request.getSampleSize() : 100;

      String indexName =
          indexEnvironmentRepository
              .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
              .map(IndexEnvironment::getIndexName)
              .orElseThrow(() -> new RuntimeException("DEV 환경을 찾을 수 없습니다"));

      int pageSize = Math.min(300, maxToProcess);
      SearchResponse<com.fasterxml.jackson.databind.JsonNode> searchResponse =
          elasticsearchClient.search(
              s -> s.index(indexName).size(pageSize).query(q -> q.matchAll(m -> m))
                  .source(src -> src.filter(f -> f.includes("name"))).scroll(sc -> sc.time("10m")),
              com.fasterxml.jackson.databind.JsonNode.class);

      List<co.elastic.clients.elasticsearch.core.search.Hit<com.fasterxml.jackson.databind.JsonNode>> hits =
          searchResponse.hits().hits();
      List<String> productNames = hits.stream()
          .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
          .filter(Objects::nonNull)
          .map(src -> src.get("name"))
          .filter(Objects::nonNull)
          .map(com.fasterxml.jackson.databind.JsonNode::asText)
          .filter(s -> s != null && !s.isEmpty())
          .collect(Collectors.toList());

      // 프롬프트 작성 및 호출
      String template = promptTemplateLoader.loadTemplate("typo-recommendation.txt");
      StringBuilder buf = new StringBuilder();
      productNames.stream().limit(100).forEach(n -> buf.append("- ").append(n).append("\n"));
      String prompt = template.replace("{PRODUCT_NAMES}", buf.toString().trim());
      String llmResp = llmService.callLLMAPI(prompt);

      // 파싱: JSON [{pair, description}] 또는 줄별 "원문,교정|이유" (공백 교정 페어)
      List<PairCandidate> candidates = parseCandidates(llmResp);

      Map<String, PairCandidate> first = new LinkedHashMap<>();
      Map<String, Integer> count = new LinkedHashMap<>();
      for (PairCandidate c : candidates) {
        if (c.pair == null || c.pair.isBlank()) continue;
        // 공백 기준 교정 페어 형식 검증: 두 파트 모두 비어있지 않아야 함
        String[] parts = c.pair.split(",", 2);
        if (parts.length != 2) continue;
        String left = parts[0].trim();
        String right = parts[1].trim();
        if (left.isEmpty() || right.isEmpty()) continue;
        first.putIfAbsent(c.pair, c);
        count.merge(c.pair, 1, Integer::sum);
      }

      for (Map.Entry<String, Integer> e : count.entrySet()) {
        String pair = e.getKey();
        int inc = e.getValue();
        Optional<TypoCorrectionRecommendation> existing = recommendationRepository.findByPair(pair);
        if (existing.isPresent()) {
          TypoCorrectionRecommendation r = existing.get();
          r.setRecommendationCount(r.getRecommendationCount() + inc);
          recommendationRepository.save(r);
        } else {
          TypoCorrectionRecommendation r = TypoCorrectionRecommendation.builder()
              .pair(pair)
              .reason(first.get(pair).reason)
              .build();
          r.setRecommendationCount(inc);
          recommendationRepository.save(r);
        }
      }

    } catch (Exception e) {
      log.error("오타 교정어 추천 생성 실패", e);
      throw new RuntimeException("오타 교정어 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  private List<PairCandidate> parseCandidates(String resp) {
    List<PairCandidate> out = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp);
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
          String pair = n.path("pair").asText("");
          String reason = n.path("description").asText("");
          if (!pair.isEmpty()) out.add(new PairCandidate(pair, reason));
        }
        return out;
      }
    } catch (Exception ignore) {}
    for (String line : resp.split("\n")) {
      String s = line.trim();
      if (s.isEmpty()) continue;
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

  @Transactional(readOnly = true)
  public TypoCorrectionRecommendationListResponse getRecommendations() {
    List<TypoCorrectionRecommendation> list = recommendationRepository.findAllByOrderByRecommendationCountDesc();
    List<TypoCorrectionRecommendationListResponse.TypoCorrectionDetail> details = list.stream()
        .map(r -> TypoCorrectionRecommendationListResponse.TypoCorrectionDetail.builder()
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
}


