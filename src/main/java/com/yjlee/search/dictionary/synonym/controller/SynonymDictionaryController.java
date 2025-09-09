package com.yjlee.search.dictionary.synonym.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.deployment.service.ElasticsearchSynonymService;
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
  private final ElasticsearchSynonymService elasticsearchSynonymService;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<SynonymDictionaryListResponse> getSynonymDictionaries(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "updatedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestParam(required = false) DictionaryEnvironmentType environment) {
    return synonymDictionaryService.getList(page, size, sortBy, sortDir, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{dictionaryId}")
  public SynonymDictionaryResponse getSynonymDictionaryDetail(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return synonymDictionaryService.get(dictionaryId, environment);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SynonymDictionaryResponse createSynonymDictionary(
      @RequestBody @Valid SynonymDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return synonymDictionaryService.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{dictionaryId}")
  public SynonymDictionaryResponse updateSynonymDictionary(
      @PathVariable Long dictionaryId,
      @RequestBody @Valid SynonymDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    return synonymDictionaryService.update(dictionaryId, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{dictionaryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSynonymDictionary(
      @PathVariable Long dictionaryId,
      @RequestParam(defaultValue = "CURRENT") DictionaryEnvironmentType environment) {
    synonymDictionaryService.delete(dictionaryId, environment);
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  public SynonymSyncResponse syncSynonymDictionary(
      @RequestParam DictionaryEnvironmentType environment) {
    try {
      String synonymSetName = getSynonymSetName(environment);

      if (environment != DictionaryEnvironmentType.CURRENT) {
        IndexEnvironment.EnvironmentType envType =
            environment == DictionaryEnvironmentType.DEV
                ? IndexEnvironment.EnvironmentType.DEV
                : IndexEnvironment.EnvironmentType.PROD;

        indexEnvironmentRepository
            .findByEnvironmentType(envType)
            .filter(env -> env.getVersion() != null)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        environment.getDescription() + " 환경에 인덱스가 없습니다. 먼저 인덱스를 생성하고 배포해주세요."));
      }

      elasticsearchSynonymService.createOrUpdateSynonymSet(synonymSetName, environment);
      return SynonymSyncResponse.success(environment.getDescription());
    } catch (Exception e) {
      return SynonymSyncResponse.error(environment.getDescription(), e.getMessage());
    }
  }

  private String getSynonymSetName(DictionaryEnvironmentType environment) {
    if (environment == DictionaryEnvironmentType.CURRENT) {
      return "synonyms-nori-current";
    }

    IndexEnvironment.EnvironmentType envType =
        environment == DictionaryEnvironmentType.DEV
            ? IndexEnvironment.EnvironmentType.DEV
            : IndexEnvironment.EnvironmentType.PROD;

    return indexEnvironmentRepository
        .findByEnvironmentType(envType)
        .map(env -> "synonyms-nori-" + env.getVersion())
        .orElse("synonyms-nori-" + environment.name().toLowerCase());
  }
}
