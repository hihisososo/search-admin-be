package com.yjlee.search.test.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.test.dto.DictionaryExtractionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryExtractionService {

  private final ProductRepository productRepository;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${openai.api.key:}")
  private String openaiApiKey;

  @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
  private String openaiApiUrl;

  public List<String> getProductNames(int limit) {
    log.info("상품명 조회 시작 - 제한: {}", limit);
    
    var products = productRepository.findAll(PageRequest.of(0, limit));
    var productNames = products.getContent().stream()
        .map(product -> product.getName())
        .filter(name -> name != null && !name.trim().isEmpty())
        .toList();
    
    log.info("상품명 조회 완료 - 조회된 개수: {}", productNames.size());
    return productNames;
  }

  public DictionaryExtractionResponse extractDictionaryEntries(int limit) {
    log.info("사전 엔트리 추출 시작 - 제한: {}", limit);
    
    try {
      // 1. 상품명 조회
      List<String> productNames = getProductNames(limit);
      
      if (productNames.isEmpty()) {
        log.warn("분석할 상품명이 없습니다");
        return createEmptyResponse(0);
      }

      // 2. LLM 호출
      String llmResponse = callLlmForDictionaryExtraction(productNames);
      
      // 3. 응답 파싱
      return parseLlmResponse(llmResponse, productNames.size());
      
    } catch (Exception e) {
      log.error("사전 엔트리 추출 중 오류 발생", e);
      throw new RuntimeException("사전 엔트리 추출 실패: " + e.getMessage(), e);
    }
  }

  private String callLlmForDictionaryExtraction(List<String> productNames) {
    if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
      log.warn("OpenAI API 키가 설정되지 않았습니다. 목업 응답을 반환합니다.");
      return createMockLlmResponse();
    }

    try {
      String prompt = createAnalysisPrompt(productNames);
      
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(openaiApiKey);

      Map<String, Object> requestBody = Map.of(
          "model", "gpt-4o-mini",
          "messages", List.of(
              Map.of("role", "user", "content", prompt)
          ),
          "max_tokens", 4000,
          "temperature", 0.3
      );

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
      
      log.info("OpenAI API 호출 시작");
      ResponseEntity<String> response = restTemplate.exchange(
          openaiApiUrl, HttpMethod.POST, entity, String.class);
      
      if (response.getStatusCode() == HttpStatus.OK) {
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String content = jsonNode.path("choices").get(0).path("message").path("content").asText();
        log.info("OpenAI API 호출 성공");
        return content;
      } else {
        throw new RuntimeException("OpenAI API 호출 실패: " + response.getStatusCode());
      }
      
    } catch (Exception e) {
      log.error("OpenAI API 호출 중 오류 발생, 목업 응답으로 대체", e);
      return createMockLlmResponse();
    }
  }

  private String createAnalysisPrompt(List<String> productNames) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("Please analyze the following product names and extract dictionary entries for a search system.\n\n");
    prompt.append("Product Names:\n");
    
    for (int i = 0; i < Math.min(productNames.size(), 50); i++) {
      prompt.append("- ").append(productNames.get(i)).append("\n");
    }
    
    if (productNames.size() > 50) {
      prompt.append("... and ").append(productNames.size() - 50).append(" more items\n");
    }
    
    prompt.append("\nPlease respond in the following format:\n\n");
    prompt.append("## 사용자 사전\n");
    prompt.append("Brand names, model names, technical terms that morphological analyzers might not recognize:\n");
    prompt.append("- 갤럭시: 삼성 스마트폰 브랜드명\n");
    prompt.append("- 아이폰: 애플 스마트폰 브랜드명\n\n");
    
    prompt.append("## 동의어 사전\n");
    prompt.append("Groups of words used with the same meaning:\n");
    prompt.append("- 스마트폰: 핸드폰,휴대폰,모바일폰 (스마트폰과 동일한 의미)\n");
    prompt.append("- 노트북: 랩톱,랩탑 (휴대용 컴퓨터)\n\n");
    
    prompt.append("## 불용어 사전\n");
    prompt.append("Meaningless words that should be excluded from search:\n");
    prompt.append("- 그리고: 접속사로 검색 의미 없음\n");
    prompt.append("- 하지만: 접속사로 검색 의미 없음\n\n");
    
    prompt.append("## 오타교정 사전\n");
    prompt.append("Common typos and their correct spellings:\n");
    prompt.append("- 삼송: 삼성 (삼성 브랜드명 오타)\n");
    prompt.append("- 애플폰: 아이폰 (아이폰 브랜드명 오타)\n\n");
    
    prompt.append("Extract a maximum of 10 entries per dictionary and select only the most useful ones.");
    
    return prompt.toString();
  }

  private String createMockLlmResponse() {
    return """
        ## 사용자 사전
        - 갤럭시: 삼성 스마트폰 브랜드명
        - 아이폰: 애플 스마트폰 브랜드명
        - 맥북: 애플 노트북 브랜드명
        - 에어팟: 애플 무선 이어폰 브랜드명
        - 플레이스테이션: 소니 게임 콘솔 브랜드명
        
        ## 동의어 사전
        - 스마트폰: 핸드폰,휴대폰,모바일폰 (스마트폰과 동일한 의미)
        - 노트북: 랩톱,랩탑 (휴대용 컴퓨터)
        - 이어폰: 헤드폰,이어셋 (음향 기기)
        - 충전기: 어댑터,충전 어댑터 (전력 공급 장치)
        
        ## 불용어 사전
        - 그리고: 접속사로 검색 의미 없음
        - 하지만: 접속사로 검색 의미 없음
        - 그런데: 접속사로 검색 의미 없음
        - 또는: 접속사로 검색 의미 없음
        
        ## 오타교정 사전
        - 삼송: 삼성 (삼성 브랜드명 오타)
        - 애플폰: 아이폰 (아이폰 브랜드명 오타)
        - 겔럭시: 갤럭시 (갤럭시 브랜드명 오타)
        - 맥뷱: 맥북 (맥북 브랜드명 오타)
        """;
  }

  private DictionaryExtractionResponse parseLlmResponse(String llmResponse, int analyzedCount) {
    log.info("LLM 응답 파싱 시작");
    
    try {
      return DictionaryExtractionResponse.builder()
          .success(true)
          .analyzedProductCount(analyzedCount)
          .userDictionaryEntries(parseUserDictionary(llmResponse))
          .synonymDictionaryEntries(parseSynonymDictionary(llmResponse))
          .stopwordDictionaryEntries(parseStopwordDictionary(llmResponse))
          .typoCorrectionDictionaryEntries(parseTypoCorrectionDictionary(llmResponse))
          .processedAt(LocalDateTime.now())
          .rawLlmResponse(llmResponse)
          .build();
          
    } catch (Exception e) {
      log.error("LLM 응답 파싱 중 오류 발생", e);
      return createEmptyResponse(analyzedCount);
    }
  }

  private List<DictionaryExtractionResponse.UserDictionaryEntry> parseUserDictionary(String response) {
    List<DictionaryExtractionResponse.UserDictionaryEntry> entries = new ArrayList<>();
    String userSection = extractSection(response, "사용자 사전");
    
    Pattern pattern = Pattern.compile("- ([^:]+): (.+)");
    Matcher matcher = pattern.matcher(userSection);
    
    while (matcher.find()) {
      entries.add(DictionaryExtractionResponse.UserDictionaryEntry.builder()
          .keyword(matcher.group(1).trim())
          .description(matcher.group(2).trim())
          .build());
    }
    
    return entries;
  }

  private List<DictionaryExtractionResponse.SynonymDictionaryEntry> parseSynonymDictionary(String response) {
    List<DictionaryExtractionResponse.SynonymDictionaryEntry> entries = new ArrayList<>();
    String synonymSection = extractSection(response, "동의어 사전");
    
    Pattern pattern = Pattern.compile("- ([^:]+): ([^(]+)\\(([^)]+)\\)");
    Matcher matcher = pattern.matcher(synonymSection);
    
    while (matcher.find()) {
      entries.add(DictionaryExtractionResponse.SynonymDictionaryEntry.builder()
          .keyword(matcher.group(1).trim())
          .synonyms(matcher.group(2).trim())
          .description(matcher.group(3).trim())
          .build());
    }
    
    return entries;
  }

  private List<DictionaryExtractionResponse.StopwordDictionaryEntry> parseStopwordDictionary(String response) {
    List<DictionaryExtractionResponse.StopwordDictionaryEntry> entries = new ArrayList<>();
    String stopwordSection = extractSection(response, "불용어 사전");
    
    Pattern pattern = Pattern.compile("- ([^:]+): (.+)");
    Matcher matcher = pattern.matcher(stopwordSection);
    
    while (matcher.find()) {
      entries.add(DictionaryExtractionResponse.StopwordDictionaryEntry.builder()
          .keyword(matcher.group(1).trim())
          .description(matcher.group(2).trim())
          .build());
    }
    
    return entries;
  }

  private List<DictionaryExtractionResponse.TypoCorrectionDictionaryEntry> parseTypoCorrectionDictionary(String response) {
    List<DictionaryExtractionResponse.TypoCorrectionDictionaryEntry> entries = new ArrayList<>();
    String typoSection = extractSection(response, "오타교정 사전");
    
    Pattern pattern = Pattern.compile("- ([^:]+): ([^(]+)\\(([^)]+)\\)");
    Matcher matcher = pattern.matcher(typoSection);
    
    while (matcher.find()) {
      entries.add(DictionaryExtractionResponse.TypoCorrectionDictionaryEntry.builder()
          .keyword(matcher.group(1).trim())
          .correctedWord(matcher.group(2).trim())
          .description(matcher.group(3).trim())
          .build());
    }
    
    return entries;
  }

  private String extractSection(String response, String sectionName) {
    Pattern sectionPattern = Pattern.compile("## " + sectionName + "\\s*\\n([\\s\\S]*?)(?=\\n##|$)");
    Matcher matcher = sectionPattern.matcher(response);
    
    if (matcher.find()) {
      return matcher.group(1);
    }
    
    return "";
  }

  private DictionaryExtractionResponse createEmptyResponse(int analyzedCount) {
    return DictionaryExtractionResponse.builder()
        .success(false)
        .analyzedProductCount(analyzedCount)
        .userDictionaryEntries(new ArrayList<>())
        .synonymDictionaryEntries(new ArrayList<>())
        .stopwordDictionaryEntries(new ArrayList<>())
        .typoCorrectionDictionaryEntries(new ArrayList<>())
        .processedAt(LocalDateTime.now())
        .rawLlmResponse("")
        .build();
  }
} 