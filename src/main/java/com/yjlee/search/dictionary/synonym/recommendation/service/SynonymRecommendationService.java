package com.yjlee.search.dictionary.synonym.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationListResponse;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationRequest;
import com.yjlee.search.dictionary.synonym.recommendation.dto.SynonymRecommendationResponse;
import com.yjlee.search.dictionary.synonym.recommendation.model.SynonymRecommendation;
import com.yjlee.search.dictionary.synonym.recommendation.repository.SynonymRecommendationRepository;
import com.yjlee.search.evaluation.service.LLMService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymRecommendationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final SynonymRecommendationRepository recommendationRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;
  private final ElasticsearchAnalyzeService analyzeService;
  private final Object llmLogFileLock = new Object();

  @Transactional
  public SynonymRecommendationResponse generateRecommendations(SynonymRecommendationRequest request) {
    String[] lastScrollIdHolder = new String[] {null};
    try {
      int maxToProcess = 1000;
      if (request != null && request.getSampleSize() != null && request.getSampleSize() > 0) {
        maxToProcess = request.getSampleSize();
      }
      log.info("유의어 추천 생성 요청 - 처리 상한: {}건", maxToProcess);

      String indexName =
          indexEnvironmentRepository
              .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
              .map(IndexEnvironment::getIndexName)
              .orElseThrow(() -> new RuntimeException("DEV 환경을 찾을 수 없습니다"));

      int pageSize = Math.min(1000, maxToProcess);
      SearchResponse<com.fasterxml.jackson.databind.JsonNode> searchResponse =
          elasticsearchClient.search(
              s ->
                  s.index(indexName)
                      .size(pageSize)
                      .query(q -> q.matchAll(m -> m))
                      .source(src -> src.filter(f -> f.includes("name")))
                      .scroll(sc -> sc.time("10m")),
              com.fasterxml.jackson.databind.JsonNode.class);

      lastScrollIdHolder[0] = searchResponse.scrollId();

      long totalHits =
          Optional.ofNullable(searchResponse.hits()).map(h -> h.total()).map(t -> t.value()).orElse(-1L);

      int totalProcessedProducts = 0;
      int totalGenerated = 0;
      int totalNewSaved = 0;

      List<co.elastic.clients.elasticsearch.core.search.Hit<com.fasterxml.jackson.databind.JsonNode>> hits =
          searchResponse.hits().hits();

      while (hits != null && !hits.isEmpty()) {
        List<String> productNamesPage =
            hits.stream()
                .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
                .filter(Objects::nonNull)
                .map(source -> source.get("name"))
                .filter(Objects::nonNull)
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());

        totalProcessedProducts += productNamesPage.size();

        List<List<String>> batches = partitionIntoBatches(productNamesPage, 20);

        List<RecommendedSynonymGroup> pageRecommendations =
            batches.parallelStream()
                .map(
                    batch -> {
                      try {
                        String prompt = buildPrompt(batch);
                        String llmResponse = llmService.callLLMAPI(prompt);
                        writeRawLLMResponseToFile(llmResponse);
                        return parseRecommendations(llmResponse);
                      } catch (Exception e) {
                        log.warn("배치 처리 실패, 빈 결과로 대체: {}", e.getMessage());
                        return Collections.<RecommendedSynonymGroup>emptyList();
                      }
                    })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // 공백 포함 토큰 제거 및 영숫자만 토큰 제외, 최소 2개 이상 토큰 유지
        pageRecommendations =
            pageRecommendations.stream()
                .map(this::normalizeGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        totalGenerated += pageRecommendations.size();

        List<RecommendedSynonymGroup> filtered = filterByNoriAnalyzer(pageRecommendations);
        List<SynonymRecommendation> saved = saveRecommendations(filtered);
        totalNewSaved += saved.size();

        if (totalHits > 0) {
          log.info("진행률: {} / {} 처리됨", totalProcessedProducts, totalHits);
        } else {
          log.info("진행률: {} 처리됨", totalProcessedProducts);
        }

        if (totalProcessedProducts >= maxToProcess) {
          log.info("요청한 상한({}건)에 도달하여 처리를 중단합니다.", maxToProcess);
          break;
        }

        co.elastic.clients.elasticsearch.core.ScrollRequest scrollReq =
            new co.elastic.clients.elasticsearch.core.ScrollRequest.Builder()
                .scrollId(lastScrollIdHolder[0])
                .scroll(s -> s.time("10m"))
                .build();
        co.elastic.clients.elasticsearch.core.ScrollResponse<com.fasterxml.jackson.databind.JsonNode>
            scrollResponse =
                elasticsearchClient.scroll(scrollReq, com.fasterxml.jackson.databind.JsonNode.class);
        lastScrollIdHolder[0] = scrollResponse.scrollId();
        hits = scrollResponse.hits().hits();
      }

      return SynonymRecommendationResponse.builder()
          .totalGenerated(totalGenerated)
          .totalSaved(totalNewSaved)
          .duplicatesSkipped(Math.max(0, totalGenerated - totalNewSaved))
          .recommendations(Collections.emptyList())
          .build();

    } catch (Exception e) {
      log.error("유의어 추천 생성 실패", e);
      throw new RuntimeException("유의어 추천 생성 실패: " + e.getMessage());
    } finally {
      if (lastScrollIdHolder[0] != null) {
        try {
          co.elastic.clients.elasticsearch.core.ClearScrollRequest clearReq =
              new co.elastic.clients.elasticsearch.core.ClearScrollRequest.Builder()
                  .scrollId(lastScrollIdHolder[0])
                  .build();
          elasticsearchClient.clearScroll(clearReq);
        } catch (Exception ignore) {
          log.warn("스크롤 정리(clear) 실패: {}", ignore.getMessage());
        }
      }
    }
  }

  private List<List<String>> partitionIntoBatches(List<String> items, int batchSize) {
    if (items == null || items.isEmpty() || batchSize <= 0) {
      return Collections.emptyList();
    }
    List<List<String>> result = new ArrayList<>();
    for (int i = 0; i < items.size(); i += batchSize) {
      int end = Math.min(i + batchSize, items.size());
      result.add(items.subList(i, end));
    }
    return result;
  }

  private void writeRawLLMResponseToFile(String content) {
    try {
      Path dir = Paths.get("logs", "llm-responses");
      Files.createDirectories(dir);
      Path file = dir.resolve("llm_synonym_responses.log");
      String timestamp =
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
      StringBuilder entry =
          new StringBuilder()
              .append("===== LLM SYNONYM RESPONSE [")
              .append(timestamp)
              .append("] =====\n")
              .append(content == null ? "" : content)
              .append("\n\n");
      byte[] bytes = entry.toString().getBytes(StandardCharsets.UTF_8);
      synchronized (llmLogFileLock) {
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    } catch (Exception e) {
      log.warn("LLM 유의어 응답 파일 기록 실패: {}", e.getMessage());
    }
  }

  private String buildPrompt(List<String> productNames) {
    String promptTemplate = promptTemplateLoader.loadTemplate("synonym-recommendation.txt");
    List<String> limited = productNames.stream().limit(20).collect(Collectors.toList());
    StringBuilder productNamesList = new StringBuilder();
    for (String name : limited) {
      productNamesList.append("- ").append(name).append("\n");
    }
    return promptTemplate.replace("{PRODUCT_NAMES}", productNamesList.toString().trim());
  }

  private List<RecommendedSynonymGroup> parseRecommendations(String llmResponse) {
    List<RecommendedSynonymGroup> result = new ArrayList<>();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(llmResponse);
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode node : root) {
          String group = node.path("group").asText("");
          String reason = node.path("description").asText("");
          if (!group.isEmpty()) {
            result.add(new RecommendedSynonymGroup(group, reason));
          }
        }
      }
    } catch (Exception e) {
      log.error("유의어 JSON 파싱 실패, 텍스트 파싱 시도: {}", e.getMessage());
      String[] lines = llmResponse.split("\n");
      for (String line : lines) {
        line = line.trim();
        if (line.isEmpty()) continue;
        // 포맷: "랩탑,laptop,노트북|설명" 또는 "랩탑,laptop,노트북"
        String groupPart = line;
        String reason = "";
        if (line.contains("|")) {
          String[] parts = line.split("\\|", 2);
          groupPart = parts[0].trim();
          reason = parts[1].trim();
        }
        result.add(new RecommendedSynonymGroup(groupPart, reason));
      }
    }
    return result;
  }

  private RecommendedSynonymGroup normalizeGroup(RecommendedSynonymGroup group) {
    if (group == null || group.group == null) return null;
    String[] tokens = group.group.split(",");
    List<String> cleaned =
        Arrays.stream(tokens)
            .map(String::trim)
            .filter(t -> !t.isEmpty())
            .filter(t -> !t.matches("(?i)^[a-z0-9]+$")) // 모델/코드 의심 제거
            .filter(t -> !t.contains(" "))
            .distinct()
            .collect(Collectors.toList());
    if (cleaned.size() < 2) return null; // 최소 2개 이상
    // 정렬(사전식) 후 조인하여 표준화 키 생성
    List<String> sorted = new ArrayList<>(cleaned);
    Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
    String normalized = String.join(",", sorted);
    return new RecommendedSynonymGroup(normalized, group.reason);
  }

  private List<RecommendedSynonymGroup> filterByNoriAnalyzer(
      List<RecommendedSynonymGroup> groups) {
    List<RecommendedSynonymGroup> filtered = new ArrayList<>();
    for (RecommendedSynonymGroup g : groups) {
      try {
        String[] tokens = g.group.split(",");
        // 각 토큰이 노리 기준으로 한 단어로 인식되는지 확인
        boolean allSingle = true;
        for (String token : tokens) {
          List<AnalyzeTextResponse.TokenInfo> analyzed =
              analyzeService.analyzeText(token, DictionaryEnvironmentType.DEV);
          if (analyzed.size() >= 2) {
            allSingle = false;
            break;
          }
        }
        if (!allSingle) {
          // 한 토큰이라도 분해되면 사용자 사전/유의어 매핑이 유효 가치가 있을 수 있으므로 포함
          filtered.add(g);
        } else {
          // 모두 이미 한 토큰으로 인식되면 굳이 유의어 사전 필요성이 낮다고 판단하여 제외
          log.debug("유의어 그룹 '{}' 제외: 모든 토큰이 단일 토큰으로 인식", g.group);
        }
      } catch (Exception e) {
        log.warn("유의어 그룹 '{}' 분석 실패, 추가함: {}", g.group, e.getMessage());
        filtered.add(g); // 실패 시 보수적으로 포함
      }
    }
    return filtered;
  }

  @Transactional
  private List<SynonymRecommendation> saveRecommendations(List<RecommendedSynonymGroup> groups) {
    Map<String, RecommendedSynonymGroup> firstAppearance = new LinkedHashMap<>();
    Map<String, Integer> groupToCount = new LinkedHashMap<>();
    for (RecommendedSynonymGroup g : groups) {
      firstAppearance.putIfAbsent(g.group, g);
      groupToCount.merge(g.group, 1, Integer::sum);
    }

    List<SynonymRecommendation> saved = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : groupToCount.entrySet()) {
      String group = entry.getKey();
      int count = entry.getValue();
      RecommendedSynonymGroup base = firstAppearance.get(group);

      Optional<SynonymRecommendation> existingOpt =
          recommendationRepository.findBySynonymGroup(group);
      if (existingOpt.isPresent()) {
        SynonymRecommendation existing = existingOpt.get();
        int current = existing.getRecommendationCount();
        existing.setRecommendationCount(current + count);
        recommendationRepository.save(existing);
        log.debug("기존 유의어 그룹 '{}' 카운트 증가: {} -> {}", group, current, current + count);
        continue;
      }

      SynonymRecommendation recommendation =
          SynonymRecommendation.builder().synonymGroup(group).reason(base.reason).build();
      recommendation.setRecommendationCount(count);
      saved.add(recommendationRepository.save(recommendation));
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public SynonymRecommendationListResponse getRecommendations() {
    List<SynonymRecommendation> list =
        recommendationRepository.findAllByOrderByRecommendationCountDesc();
    List<SynonymRecommendationListResponse.SynonymRecommendationDetail> details =
        list.stream()
            .map(
                r ->
                    SynonymRecommendationListResponse.SynonymRecommendationDetail.builder()
                        .synonymGroup(r.getSynonymGroup())
                        .reason(r.getReason())
                        .recommendationCount(r.getRecommendationCount())
                        .build())
            .collect(Collectors.toList());

    return SynonymRecommendationListResponse.builder()
        .totalCount(details.size())
        .recommendations(details)
        .build();
  }

  private record RecommendedSynonymGroup(String group, String reason) {}
}


