package com.yjlee.search.search.controller;

import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.search.dto.AutocompleteResponse;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchParams;
import com.yjlee.search.search.dto.SearchSimulationParams;
import com.yjlee.search.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Search", description = "검색 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  @Operation(summary = "상품 검색")
  @GetMapping
  public ResponseEntity<SearchExecuteResponse> search(
      @ParameterObject SearchParams params, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(searchService.executeSearch(params, httpRequest));
  }

  @Operation(summary = "자동완성 검색")
  @GetMapping("/autocomplete")
  public ResponseEntity<AutocompleteResponse> autocomplete(
      @RequestParam @NotBlank @Size(min = 1, max = 100) String keyword) {
    return ResponseEntity.ok(searchService.getAutocompleteSuggestions(keyword));
  }

  @Operation(summary = "상품 검색 시뮬레이션")
  @GetMapping("/simulation")
  public ResponseEntity<SearchExecuteResponse> searchSimulation(
      @ParameterObject SearchSimulationParams params, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(searchService.executeSearchSimulation(params, httpRequest));
  }

  @Operation(summary = "자동완성 검색 시뮬레이션")
  @GetMapping("/autocomplete/simulation")
  public ResponseEntity<AutocompleteResponse> autocompleteSimulation(
      @RequestParam @NotBlank @Size(min = 1, max = 100) String keyword,
      @RequestParam IndexEnvironment.EnvironmentType environmentType) {
    return ResponseEntity.ok(
        searchService.getAutocompleteSuggestionsSimulation(keyword, environmentType));
  }
}
