package com.yjlee.search.dictionary.recommendation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.recommendation.dto.RecommendationListResponse;
import com.yjlee.search.dictionary.recommendation.dto.RecommendationRequest;
import com.yjlee.search.dictionary.recommendation.dto.RecommendationResponse;
import com.yjlee.search.dictionary.recommendation.model.DictionaryRecommendation;
import com.yjlee.search.dictionary.recommendation.repository.DictionaryRecommendationRepository;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
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
public class DictionaryRecommendationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final DictionaryRecommendationRepository recommendationRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;
  private final ElasticsearchAnalyzeService analyzeService;
  private final Object llmLogFileLock = new Object();

  @Transactional
  public RecommendationResponse generateRecommendations(RecommendationRequest request) {
    String[] lastScrollIdHolder = new String[] {null};
    try {
      // 처리 상한 설정 (DTO 기반), 기본 1000
      int maxToProcess = 1000;
      if (request != null && request.getSampleSize() != null && request.getSampleSize() > 0) {
        maxToProcess = request.getSampleSize();
      }
      log.info("추천 생성 요청 - 처리 상한: {}건", maxToProcess);

      // 인덱스 조회 및 초기 스크롤 검색
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
          Optional.ofNullable(searchResponse.hits())
              .map(h -> h.total())
              .map(t -> t.value())
              .orElse(-1L);

      int totalProcessedProducts = 0;
      int totalGenerated = 0;
      int totalNewSaved = 0;
      // 페이지 번호는 로그에 사용하지 않음 (간결 로그 유지)

      List<
              co.elastic.clients.elasticsearch.core.search.Hit<
                  com.fasterxml.jackson.databind.JsonNode>>
          hits = searchResponse.hits().hits();

      while (hits != null && !hits.isEmpty()) {
        // 현재 페이지 상품명 추출
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

        // 20개씩 배치로 분할 후 병렬 처리
        List<List<String>> batches = partitionIntoBatches(productNamesPage, 20);

        List<RecommendedWord> pageRecommendations =
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
                        return Collections.<RecommendedWord>emptyList();
                      }
                    })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // 영숫자만으로 이루어진 단어는 제외 (모델명/코드 등으로 간주)
        pageRecommendations =
            pageRecommendations.stream()
                .filter(w -> !w.word.matches("(?i)^[a-z0-9]+$"))
                .collect(Collectors.toList());

        totalGenerated += pageRecommendations.size();

        // 노리 분석 필터링 및 저장
        List<RecommendedWord> filteredWords = filterByNoriAnalyzer(pageRecommendations);
        List<DictionaryRecommendation> saved = saveRecommendations(filteredWords);
        totalNewSaved += saved.size();

        if (totalHits > 0) {
          log.info("진행률: {} / {} 처리됨", totalProcessedProducts, totalHits);
        } else {
          log.info("진행률: {} 처리됨", totalProcessedProducts);
        }

        // 요청 상한까지만 처리하고 중단
        if (totalProcessedProducts >= maxToProcess) {
          log.info("요청한 상한({}건)에 도달하여 처리를 중단합니다.", maxToProcess);
          break;
        }

        // 다음 스크롤 페이지 요청
        co.elastic.clients.elasticsearch.core.ScrollRequest scrollReq =
            new co.elastic.clients.elasticsearch.core.ScrollRequest.Builder()
                .scrollId(lastScrollIdHolder[0])
                .scroll(s -> s.time("10m"))
                .build();
        co.elastic.clients.elasticsearch.core.ScrollResponse<
                com.fasterxml.jackson.databind.JsonNode>
            scrollResponse =
                elasticsearchClient.scroll(
                    scrollReq, com.fasterxml.jackson.databind.JsonNode.class);
        lastScrollIdHolder[0] = scrollResponse.scrollId();
        hits = scrollResponse.hits().hits();
      }

      return RecommendationResponse.builder()
          .totalGenerated(totalGenerated)
          .totalSaved(totalNewSaved)
          .duplicatesSkipped(Math.max(0, totalGenerated - totalNewSaved))
          .recommendations(Collections.emptyList())
          .build();

    } catch (Exception e) {
      log.error("사전 추천 생성 실패", e);
      throw new RuntimeException("사전 추천 생성 실패: " + e.getMessage());
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
      Path file = dir.resolve("llm_responses.log");
      String timestamp =
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
      StringBuilder entry =
          new StringBuilder()
              .append("===== LLM RESPONSE [")
              .append(timestamp)
              .append("] =====\n")
              .append(content == null ? "" : content)
              .append("\n\n");
      byte[] bytes = entry.toString().getBytes(StandardCharsets.UTF_8);
      synchronized (llmLogFileLock) {
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    } catch (Exception e) {
      log.warn("LLM 응답 파일 기록 실패: {}", e.getMessage());
    }
  }

  private String buildPrompt(List<String> productNames) {
    // 프롬프트 템플릿 로드
    String promptTemplate = promptTemplateLoader.loadTemplate("dictionary-recommendation.txt");

    // 상품명 목록 생성 (최대 20개)
    List<String> limitedProductNames = productNames.stream().limit(20).collect(Collectors.toList());

    StringBuilder productNamesList = new StringBuilder();
    for (String name : limitedProductNames) {
      productNamesList.append("- ").append(name).append("\n");
    }

    log.info("프롬프트에 포함된 상품명 개수: {}", limitedProductNames.size());

    // 템플릿에 상품명 목록 삽입
    String prompt = promptTemplate.replace("{PRODUCT_NAMES}", productNamesList.toString().trim());

    return prompt;
  }

  private List<RecommendedWord> parseRecommendations(String llmResponse) {
    List<RecommendedWord> recommendations = new ArrayList<>();

    try {
      // JSON 응답 파싱
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(llmResponse);

      if (rootNode.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode node : rootNode) {
          String type = node.get("type").asText();
          String word = node.get("word").asText();
          String description = node.get("description").asText();

          // 복합명사인 경우 tokens 필드에서 분해된 단어들 가져와서 word에 추가
          if ("compound".equals(type)) {

            // tokens 배열 처리
            com.fasterxml.jackson.databind.JsonNode tokensNode = node.get("tokens");
            if (tokensNode != null && tokensNode.isArray()) {
              StringBuilder wordWithTokens = new StringBuilder(word);
              for (com.fasterxml.jackson.databind.JsonNode tokenNode : tokensNode) {
                wordWithTokens.append(" ").append(tokenNode.asText());
              }
              word = wordWithTokens.toString();
            }
          }

          // 기본 검증
          if (!word.isEmpty() && word.length() <= 100) {
            recommendations.add(new RecommendedWord(word, description));
          }
        }
      }
    } catch (Exception e) {
      log.error("JSON 파싱 실패, 기존 방식으로 시도: {}", e.getMessage());

      // JSON 파싱 실패시 기존 방식으로 fallback
      String[] lines = llmResponse.split("\n");
      for (String line : lines) {
        line = line.trim();
        if (line.isEmpty() || !line.contains("|")) {
          continue;
        }

        // 간단한 텍스트 파싱 시도
        if (line.contains("|")) {
          String[] parts = line.split("\\|", 2);
          if (parts.length == 2) {
            String word = parts[0].trim();
            String description = parts[1].trim();
            if (!word.isEmpty() && word.length() <= 100) {
              recommendations.add(new RecommendedWord(word, description));
            }
          }
        }
      }
    }

    return recommendations;
  }

  @Transactional
  private List<DictionaryRecommendation> saveRecommendations(List<RecommendedWord> words) {
    List<DictionaryRecommendation> newlySavedRecommendations = new ArrayList<>();

    // 단어별 추천 카운트 집계 (첫 설명을 유지)
    Map<String, RecommendedWord> firstAppearance = new LinkedHashMap<>();
    Map<String, Integer> wordToCount = new LinkedHashMap<>();
    for (RecommendedWord word : words) {
      firstAppearance.putIfAbsent(word.word, word);
      wordToCount.merge(word.word, 1, Integer::sum);
    }

    for (Map.Entry<String, Integer> entry : wordToCount.entrySet()) {
      String word = entry.getKey();
      int count = entry.getValue();
      RecommendedWord base = firstAppearance.get(word);

      // 이미 존재하면 카운트만 증가
      Optional<DictionaryRecommendation> existingOpt = recommendationRepository.findByWord(word);
      if (existingOpt.isPresent()) {
        DictionaryRecommendation existing = existingOpt.get();
        int current = existing.getRecommendationCount();
        existing.setRecommendationCount(current + count);
        recommendationRepository.save(existing);
        log.debug("기존 단어 '{}' 카운트 증가: {} -> {}", word, current, current + count);
        continue;
      }

      // 새로 저장
      DictionaryRecommendation recommendation =
          DictionaryRecommendation.builder().word(base.word).reason(base.description).build();
      recommendation.setRecommendationCount(count);

      newlySavedRecommendations.add(recommendationRepository.save(recommendation));
    }

    return newlySavedRecommendations;
  }

  private List<RecommendedWord> filterByNoriAnalyzer(List<RecommendedWord> words) {
    List<RecommendedWord> filteredWords = new ArrayList<>();

    for (RecommendedWord word : words) {
      try {
        // 추천 문자열이 이미 "원본 분해단어1 ..." 형식일 수도 있으므로 첫 토큰을 원형으로 사용
        String candidate = word.word == null ? "" : word.word.trim();
        if (candidate.isEmpty()) {
          continue;
        }
        String original =
            candidate.contains(" ") ? candidate.substring(0, candidate.indexOf(" ")) : candidate;

        // 노리 분석기로 원형 단어 분석 (DEV 환경)
        List<AnalyzeTextResponse.TokenInfo> tokens =
            analyzeService.analyzeText(original, DictionaryEnvironmentType.DEV);

        // 복합명사인지 확인 (description에서 판단하거나 공백 포함 여부로 판단)
        boolean isCompound =
            word.description.contains("복합명사")
                || word.description.contains("복합")
                || word.word.matches(".*[a-zA-Z가-힣]+[a-zA-Z가-힣]+.*");

        // 추천어 토큰과 분석 토큰 기반 처리 분기
        List<String> recTokensList =
            Arrays.stream(candidate.split("\\s+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
        boolean candidateLooksCompound = recTokensList.size() >= 2 || isCompound;

        if (candidateLooksCompound) {
          // 복합명사: 추천 토큰 Set과 분석 토큰 Set(원형 포함)이 순서 무관 동일하면 '이미 처리됨' → 저장 X
          Set<String> recSet = new HashSet<>(recTokensList);
          Set<String> analyzedSet = new HashSet<>();
          analyzedSet.add(original);
          analyzedSet.addAll(
              tokens.stream()
                  .map(AnalyzeTextResponse.TokenInfo::getToken)
                  .map(String::trim)
                  .filter(t -> !t.isEmpty())
                  .collect(Collectors.toSet()));

          if (recSet.equals(analyzedSet)) {
            log.debug("복합명사 '{}' 제외: 추천 토큰셋과 분석 토큰셋이 동일", candidate);
            continue;
          }

          filteredWords.add(new RecommendedWord(String.join(" ", recTokensList), word.description));
          log.debug("복합명사 '{}' 추가(셋 불일치 → 사전 필요): {}", original, String.join(" ", recTokensList));
          continue;
        } else {
          // 단일 단어인 경우
          if (tokens.size() >= 2) {
            // 2개 이상으로 분리되면 추가 필요
            filteredWords.add(new RecommendedWord(original, word.description));
            log.debug("단일 단어 '{}' 추가: {} 개 토큰으로 분리됨", original, tokens.size());
          } else {
            log.debug("단일 단어 '{}' 제외: 이미 한 단어로 인식됨", original);
          }
        }
      } catch (Exception e) {
        log.warn("단어 '{}' 분석 실패, 추가함: {}", word.word, e.getMessage());
        filteredWords.add(word); // 분석 실패 시 추가
      }
    }

    return filteredWords;
  }

  private record RecommendedWord(String word, String description) {}

  @Transactional(readOnly = true)
  public RecommendationListResponse getRecommendations(String sortBy, String sortDir) {
    List<DictionaryRecommendation> recommendations;
    boolean asc = "asc".equalsIgnoreCase(sortDir);
    if ("word".equalsIgnoreCase(sortBy)) {
      recommendations = recommendationRepository.findAll();
      recommendations.sort(
          (a, b) ->
              asc
                  ? a.getWord().compareToIgnoreCase(b.getWord())
                  : b.getWord().compareToIgnoreCase(a.getWord()));
    } else {
      recommendations = recommendationRepository.findAllByOrderByRecommendationCountDesc();
      if (asc) Collections.reverse(recommendations);
    }

    List<RecommendationListResponse.RecommendationDetail> details =
        recommendations.stream()
            .map(
                r ->
                    RecommendationListResponse.RecommendationDetail.builder()
                        .word(r.getWord())
                        .reason(r.getReason())
                        .recommendationCount(r.getRecommendationCount())
                        .build())
            .collect(Collectors.toList());

    return RecommendationListResponse.builder()
        .totalCount(details.size())
        .recommendations(details)
        .build();
  }

  // apply/delete 등 상태 변경 로직은 단순 스키마 요구에 따라 제거
}
