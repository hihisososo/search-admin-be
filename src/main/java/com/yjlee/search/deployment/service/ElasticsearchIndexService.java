package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.cat.IndicesRequest;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.enums.EnvironmentType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final ResourceLoader resourceLoader;
  private final ElasticsearchSynonymService elasticsearchSynonymService;

  public String createNewIndex(String version, EnvironmentType environmentType) throws IOException {
    String productIndexName = generateProductIndexName(version);
    String autocompleteIndexName = generateAutocompleteIndexName(version);
    String versionedSynonymSet = "synonyms-nori-" + version;

    // 기존 인덱스 삭제 - 임시로 주석처리 (인덱스 보존)
    if (indexExists(productIndexName)) {
      deleteIndex(productIndexName);
    }
    if (indexExists(productIndexName)) {
      log.warn("기존 인덱스 {} 가 이미 존재하지만 삭제하지 않음 (삭제 로직 비활성화)", productIndexName);
      throw new IOException("인덱스가 이미 존재합니다: " + productIndexName);
    }

    elasticsearchSynonymService.createOrUpdateSynonymSet(versionedSynonymSet, EnvironmentType.DEV);

    String productMappingJson = createProductIndexMapping();
    String productSettingsJson = createProductIndexSettings(version, versionedSynonymSet);

    CreateIndexRequest productRequest =
        CreateIndexRequest.of(
            i ->
                i.index(productIndexName)
                    .mappings(
                        TypeMapping.of(
                            m ->
                                m.withJson(
                                    new ByteArrayInputStream(productMappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s ->
                                s.withJson(
                                    new ByteArrayInputStream(productSettingsJson.getBytes())))));

    elasticsearchClient.indices().create(productRequest);
    log.info(
        "새 상품 인덱스 생성 완료 - 인덱스: {}, 환경: {}, synonym_set: {}",
        productIndexName,
        environmentType.getDescription(),
        versionedSynonymSet);

    if (indexExists(autocompleteIndexName)) {
      deleteIndex(autocompleteIndexName);
    }
    if (indexExists(autocompleteIndexName)) {
      log.warn("기존 자동완성 인덱스 {} 가 이미 존재하지만 삭제하지 않음 (삭제 로직 비활성화)", autocompleteIndexName);
      throw new IOException("자동완성 인덱스가 이미 존재합니다: " + autocompleteIndexName);
    }

    String autocompleteMappingJson = createAutocompleteIndexMapping();
    String autocompleteSettingsJson = createAutocompleteIndexSettings(version);

    CreateIndexRequest autocompleteRequest =
        CreateIndexRequest.of(
            i ->
                i.index(autocompleteIndexName)
                    .mappings(
                        TypeMapping.of(
                            m ->
                                m.withJson(
                                    new ByteArrayInputStream(autocompleteMappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s ->
                                s.withJson(
                                    new ByteArrayInputStream(
                                        autocompleteSettingsJson.getBytes())))));

    elasticsearchClient.indices().create(autocompleteRequest);
    log.info("새 자동완성 인덱스 생성 완료 - 인덱스: {}", autocompleteIndexName);

    return productIndexName;
  }

  private String generateProductIndexName(String version) {
    return ESFields.PRODUCTS_INDEX_PREFIX + "-" + version;
  }

  private String generateAutocompleteIndexName(String version) {
    return ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-" + version;
  }

  public void deleteIndexIfExists(String indexName) throws IOException {
    if (indexName != null && indexExists(indexName)) {
      deleteIndex(indexName);
    }
  }

  public void updateProductsSearchAlias(String newIndexName) throws IOException {
    var getAliasRequest = GetAliasRequest.of(a -> a.name(ESFields.PRODUCTS_SEARCH_ALIAS));

    try {
      var aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);

      var actions = new ArrayList<Action>();

      for (String existingIndex : aliasResponse.result().keySet()) {
        if (!existingIndex.equals(newIndexName)) {
          actions.add(
              Action.of(
                  a ->
                      a.remove(r -> r.index(existingIndex).alias(ESFields.PRODUCTS_SEARCH_ALIAS))));
          log.info("기존 alias 제거 예정: {} -> {}", existingIndex, ESFields.PRODUCTS_SEARCH_ALIAS);
        }
      }

      actions.add(
          Action.of(
              a -> a.add(add -> add.index(newIndexName).alias(ESFields.PRODUCTS_SEARCH_ALIAS))));

      if (!actions.isEmpty()) {
        var updateRequest = UpdateAliasesRequest.of(u -> u.actions(actions));
        elasticsearchClient.indices().updateAliases(updateRequest);
        log.info(
            "products-search alias atomic 업데이트 완료: {} ({}개 액션 실행)", newIndexName, actions.size());
      } else {
        log.info("alias 업데이트 불필요: {} 이미 연결됨", newIndexName);
      }

    } catch (ElasticsearchException e) {
      if (e.response().status() == 404) {
        var putAliasRequest =
            PutAliasRequest.of(a -> a.index(newIndexName).name(ESFields.PRODUCTS_SEARCH_ALIAS));
        elasticsearchClient.indices().putAlias(putAliasRequest);
        log.info("products-search alias 신규 생성 완료: {}", newIndexName);
      } else {
        throw e;
      }
    }
  }

  public boolean indexExists(String indexName) throws IOException {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  public Set<String> getCurrentAliasIndices() throws IOException {
    try {
      var getAliasRequest = GetAliasRequest.of(a -> a.name(ESFields.PRODUCTS_SEARCH_ALIAS));
      var aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);
      Set<String> indices = aliasResponse.result().keySet();
      log.info("현재 {} alias가 연결된 인덱스들: {}", ESFields.PRODUCTS_SEARCH_ALIAS, indices);
      return indices;
    } catch (ElasticsearchException e) {
      if (e.response().status() == 404) {
        log.info("{} alias가 존재하지 않음", ESFields.PRODUCTS_SEARCH_ALIAS);
        return Set.of();
      } else {
        throw e;
      }
    }
  }

  private void deleteIndex(String indexName) throws IOException {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    elasticsearchClient.indices().delete(request);
    log.info("인덱스 삭제 완료: {}", indexName);

    try {
      if (indexName != null && indexName.startsWith(ESFields.PRODUCTS_INDEX_PREFIX + "-")) {
        String version = indexName.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
        String versionedSynonymSet = "synonyms-nori-" + version;
        elasticsearchSynonymService.deleteSynonymSet(versionedSynonymSet);
      }
    } catch (Exception e) {
      log.warn("동의어 세트 삭제 연동 실패(무시) - index: {}, msg: {}", indexName, e.getMessage());
    }
  }

  private String createProductIndexMapping() throws IOException {
    try {
      var resource = resourceLoader.getResource("classpath:elasticsearch/product-mapping.json");
      return StreamUtils.copyToString(
          resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("상품 매핑 파일 읽기 실패", e);
      throw new IOException("상품 매핑 파일을 읽을 수 없습니다.", e);
    }
  }

  private String createAutocompleteIndexMapping() throws IOException {
    try {
      var resource =
          resourceLoader.getResource("classpath:elasticsearch/autocomplete-mapping.json");
      return StreamUtils.copyToString(
          resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("자동완성 매핑 파일 읽기 실패", e);
      throw new IOException("자동완성 매핑 파일을 읽을 수 없습니다.", e);
    }
  }

  private String createProductIndexSettings(String version, String synonymSetName)
      throws IOException {
    try {
      var resource = resourceLoader.getResource("classpath:elasticsearch/product-settings.json");
      String settingsTemplate =
          StreamUtils.copyToString(
              resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);

      String userDictPath = "/usr/share/elasticsearch/config/analysis/user/" + version + ".txt";
      String stopwordDictPath =
          "/usr/share/elasticsearch/config/analysis/stopword/" + version + ".txt";
      String unitDictPath = "/usr/share/elasticsearch/config/analysis/unit/" + version + ".txt";

      return settingsTemplate
          .replace("{USER_DICT_PATH}", userDictPath)
          .replace("{STOPWORD_DICT_PATH}", stopwordDictPath)
          .replace("{UNIT_DICT_PATH}", unitDictPath)
          .replace("{SYNONYM_SET_NAME}", synonymSetName);
    } catch (Exception e) {
      log.error("상품 설정 파일 읽기 실패", e);
      throw new IOException("상품 설정 파일을 읽을 수 없습니다.", e);
    }
  }

  private String createAutocompleteIndexSettings(String version) throws IOException {
    try {
      var resource =
          resourceLoader.getResource("classpath:elasticsearch/autocomplete-settings.json");
      String settingsTemplate =
          StreamUtils.copyToString(
              resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);

      // 상품 인덱스와 동일한 버전의 사전 경로 사용
      String userDictPath = "/usr/share/elasticsearch/config/analysis/user/" + version + ".txt";

      return settingsTemplate.replace("{USER_DICT_PATH}", userDictPath);
    } catch (Exception e) {
      log.error("자동완성 설정 파일 읽기 실패", e);
      throw new IOException("자동완성 설정 파일을 읽을 수 없습니다.", e);
    }
  }

  public void updateAutocompleteSearchAlias(String newIndexName) throws IOException {
    var getAliasRequest = GetAliasRequest.of(a -> a.name(ESFields.AUTOCOMPLETE_SEARCH_ALIAS));

    try {
      var aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);

      var actions = new ArrayList<Action>();

      for (String existingIndex : aliasResponse.result().keySet()) {
        if (!existingIndex.equals(newIndexName)) {
          actions.add(
              Action.of(
                  a ->
                      a.remove(
                          r -> r.index(existingIndex).alias(ESFields.AUTOCOMPLETE_SEARCH_ALIAS))));
          log.info("기존 alias 제거 예정: {} -> {}", existingIndex, ESFields.AUTOCOMPLETE_SEARCH_ALIAS);
        }
      }

      actions.add(
          Action.of(
              a ->
                  a.add(add -> add.index(newIndexName).alias(ESFields.AUTOCOMPLETE_SEARCH_ALIAS))));

      if (!actions.isEmpty()) {
        var updateRequest = UpdateAliasesRequest.of(u -> u.actions(actions));
        elasticsearchClient.indices().updateAliases(updateRequest);
        log.info(
            "autocomplete-search alias atomic 업데이트 완료: {} ({}개 액션 실행)",
            newIndexName,
            actions.size());
      } else {
        log.info("alias 업데이트 불필요: {} 이미 연결됨", newIndexName);
      }

    } catch (ElasticsearchException e) {
      if (e.response().status() == 404) {
        var putAliasRequest =
            PutAliasRequest.of(a -> a.index(newIndexName).name(ESFields.AUTOCOMPLETE_SEARCH_ALIAS));
        elasticsearchClient.indices().putAlias(putAliasRequest);
        log.info("autocomplete-search alias 신규 생성 완료: {}", newIndexName);
      } else {
        throw e;
      }
    }
  }

  public String getAutocompleteIndexNameFromProductIndex(String productIndexName) {
    String version = productIndexName.replace(ESFields.PRODUCTS_INDEX_PREFIX + "-", "");
    return generateAutocompleteIndexName(version);
  }

  /**
   * 모든 인덱스 목록 조회
   *
   * @return 인덱스 이름 목록
   */
  public List<String> getAllIndices() throws IOException {
    try {
      IndicesRequest request = IndicesRequest.of(i -> i.expandWildcards(ExpandWildcard.Open));
      List<IndicesRecord> response = elasticsearchClient.cat().indices(request).valueBody();

      return response.stream()
          .map(IndicesRecord::index)
          .filter(
              index ->
                  index != null
                      && (index.startsWith(ESFields.PRODUCTS_INDEX_PREFIX + "-")
                          || index.startsWith(ESFields.AUTOCOMPLETE_INDEX_PREFIX + "-")))
          .sorted()
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("인덱스 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }

  /**
   * 사용하지 않는 인덱스 목록 조회
   *
   * @param usedIndices 현재 사용 중인 인덱스 목록
   * @return 삭제 가능한 인덱스 목록
   */
  public List<String> getUnusedIndices(Set<String> usedIndices) throws IOException {
    List<String> allIndices = getAllIndices();

    // products-search, autocomplete-search alias에 연결된 인덱스 추가
    Set<String> aliasedIndices = getCurrentAliasIndices();
    Set<String> autocompleteAliasedIndices = getCurrentAutocompleteAliasIndices();

    return allIndices.stream()
        .filter(index -> !usedIndices.contains(index))
        .filter(index -> !aliasedIndices.contains(index))
        .filter(index -> !autocompleteAliasedIndices.contains(index))
        .filter(index -> !index.endsWith("-temp")) // 임시 인덱스 제외
        .collect(Collectors.toList());
  }

  /** autocomplete-search alias에 연결된 인덱스 목록 조회 */
  public Set<String> getCurrentAutocompleteAliasIndices() throws IOException {
    try {
      var getAliasRequest = GetAliasRequest.of(a -> a.name(ESFields.AUTOCOMPLETE_SEARCH_ALIAS));
      var aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);
      Set<String> indices = aliasResponse.result().keySet();
      log.info("현재 {} alias가 연결된 인덱스들: {}", ESFields.AUTOCOMPLETE_SEARCH_ALIAS, indices);
      return indices;
    } catch (ElasticsearchException e) {
      if (e.response().status() == 404) {
        log.info("{} alias가 존재하지 않음", ESFields.AUTOCOMPLETE_SEARCH_ALIAS);
        return Set.of();
      } else {
        throw e;
      }
    }
  }

  /**
   * 사용하지 않는 인덱스 삭제
   *
   * @param unusedIndices 삭제할 인덱스 목록
   * @return 실제로 삭제된 인덱스 목록
   */
  public List<String> deleteUnusedIndices(List<String> unusedIndices) throws IOException {
    List<String> deletedIndices = new ArrayList<>();

    for (String indexName : unusedIndices) {
      try {
        deleteIndex(indexName);
        deletedIndices.add(indexName);
        log.info("미사용 인덱스 삭제 성공: {}", indexName);
      } catch (Exception e) {
        log.error("미사용 인덱스 삭제 실패: {}", indexName, e);
      }
    }

    return deletedIndices;
  }
}
