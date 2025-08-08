package com.yjlee.search.dictionary.stopword.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.stopword.recommendation.dto.StopwordRecommendationListResponse;
import com.yjlee.search.dictionary.stopword.recommendation.dto.StopwordRecommendationRequest;
import com.yjlee.search.dictionary.stopword.recommendation.model.StopwordRecommendation;
import com.yjlee.search.dictionary.stopword.recommendation.repository.StopwordRecommendationRepository;
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
public class StopwordRecommendationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final StopwordRecommendationRepository recommendationRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  @Transactional
  public void generateRecommendations(StopwordRecommendationRequest request) {
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
      String promptTemplate = promptTemplateLoader.loadTemplate("stopword-recommendation.txt");
      StringBuilder buf = new StringBuilder();
      productNames.stream().limit(100).forEach(n -> buf.append("- ").append(n).append("\n"));
      String prompt = promptTemplate.replace("{PRODUCT_NAMES}", buf.toString().trim());
      String llmResp = llmService.callLLMAPI(prompt);

      // 파싱: 라인별 불용어|이유 또는 JSON [{term, description}]
      List<StopwordCandidate> candidates = parseCandidates(llmResp);
      // 저장: 동일 term 카운트 증가
      Map<String, StopwordCandidate> first = new LinkedHashMap<>();
      Map<String, Integer> count = new LinkedHashMap<>();
      for (StopwordCandidate c : candidates) {
        if (c.term == null || c.term.isBlank()) continue;
        first.putIfAbsent(c.term, c);
        count.merge(c.term, 1, Integer::sum);
      }
      for (Map.Entry<String, Integer> e : count.entrySet()) {
        String term = e.getKey();
        int inc = e.getValue();
        Optional<StopwordRecommendation> existing = recommendationRepository.findByTerm(term);
        if (existing.isPresent()) {
          StopwordRecommendation r = existing.get();
          r.setRecommendationCount(r.getRecommendationCount() + inc);
          recommendationRepository.save(r);
        } else {
          StopwordRecommendation r = StopwordRecommendation.builder()
              .term(term)
              .reason(first.get(term).reason)
              .build();
          r.setRecommendationCount(inc);
          recommendationRepository.save(r);
        }
      }

    } catch (Exception e) {
      log.error("불용어 추천 생성 실패", e);
      throw new RuntimeException("불용어 추천 생성 실패: " + e.getMessage(), e);
    }
  }

  private List<StopwordCandidate> parseCandidates(String resp) {
    List<StopwordCandidate> out = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp);
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
          String term = n.path("term").asText("");
          String reason = n.path("description").asText("");
          if (!term.isEmpty()) out.add(new StopwordCandidate(term, reason));
        }
        return out;
      }
    } catch (Exception ignore) {}
    for (String line : resp.split("\n")) {
      String s = line.trim();
      if (s.isEmpty()) continue;
      String term = s;
      String reason = "";
      if (s.contains("|")) {
        String[] parts = s.split("\\|", 2);
        term = parts[0].trim();
        reason = parts.length > 1 ? parts[1].trim() : "";
      }
      if (!term.isEmpty()) out.add(new StopwordCandidate(term, reason));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public StopwordRecommendationListResponse getRecommendations() {
    List<StopwordRecommendation> list = recommendationRepository.findAllByOrderByRecommendationCountDesc();
    List<StopwordRecommendationListResponse.StopwordDetail> details = list.stream()
        .map(r -> StopwordRecommendationListResponse.StopwordDetail.builder()
            .term(r.getTerm())
            .reason(r.getReason())
            .recommendationCount(r.getRecommendationCount())
            .build())
        .collect(Collectors.toList());
    return StopwordRecommendationListResponse.builder()
        .totalCount(details.size())
        .recommendations(details)
        .build();
  }

  private record StopwordCandidate(String term, String reason) {}
}


