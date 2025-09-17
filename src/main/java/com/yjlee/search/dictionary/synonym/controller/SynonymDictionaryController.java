package com.yjlee.search.dictionary.synonym.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.dictionary.synonym.dto.*;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "동의어 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/synonyms")
@RequiredArgsConstructor
public class SynonymDictionaryController {

  private final SynonymDictionaryService synonymDictionaryService;
  private final IndexEnvironmentService environmentService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<SynonymDictionaryListResponse> getSynonymDictionaries(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "updatedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestParam(required = false) EnvironmentType environment) {
    return synonymDictionaryService.getList(page, size, sortBy, sortDir, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public SynonymDictionaryResponse getSynonymDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return synonymDictionaryService.get(dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SynonymDictionaryResponse createSynonymDictionary(
      @RequestBody @Valid SynonymDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return synonymDictionaryService.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public SynonymDictionaryResponse updateSynonymDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid SynonymDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return synonymDictionaryService.update(dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSynonymDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    synonymDictionaryService.delete(dictionaryId, environment);
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  public SynonymSyncResponse syncSynonymDictionary(@RequestParam EnvironmentType environment) {
    try {
      synonymDictionaryService.createOrUpdateSynonymSet(environment);
      return SynonymSyncResponse.success(environment.getDescription());
    } catch (Exception e) {
      return SynonymSyncResponse.error(environment.getDescription(), e.getMessage());
    }
  }

  private String getSynonymSetName(EnvironmentType environment) {
    if (environment == EnvironmentType.CURRENT) {
      return "synonyms-nori-current";
    }

    EnvironmentType envType =
        environment == EnvironmentType.DEV ? EnvironmentType.DEV : EnvironmentType.PROD;

    var env = environmentService.getEnvironment(envType);
    return env != null && env.getVersion() != null
        ? "synonyms-nori-" + env.getVersion()
        : "synonyms-nori-" + environment.name().toLowerCase();
  }
}
