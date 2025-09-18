package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexAliasService {

  private final ElasticsearchClient elasticsearchClient;

  @Transactional(readOnly = true)
  public void updateAliases(
      String productIndexName,
      String productAliasName,
      String autocompleteIndexName,
      String autocompleteAliasName) {
    updateAlias(productIndexName, productAliasName);
    updateAlias(autocompleteIndexName, autocompleteAliasName);
  }

  public void updateAlias(String newIndexName, String aliasName) {
    validateIndexName(newIndexName);
    GetAliasRequest getAliasRequest = GetAliasRequest.of(a -> a.name(aliasName));

    try {
      ArrayList<Action> actions = new ArrayList<Action>();

      try {
        GetAliasResponse aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);

        for (String existingIndex : aliasResponse.result().keySet()) {
          if (!existingIndex.equals(newIndexName)) {
            actions.add(Action.of(a -> a.remove(r -> r.index(existingIndex).alias(aliasName))));
          }
        }
      } catch (ElasticsearchException e) {
        if (e.getMessage().contains("http_status_404") || e.getMessage().contains("missing")) {
          log.info("Alias {} 가 존재하지 않아 새로 생성합니다.", aliasName);
        } else {
          throw e;
        }
      }

      actions.add(Action.of(a -> a.add(add -> add.index(newIndexName).alias(aliasName))));

      if (!actions.isEmpty()) {
        UpdateAliasesRequest updateRequest = UpdateAliasesRequest.of(u -> u.actions(actions));
        elasticsearchClient.indices().updateAliases(updateRequest);
        log.info("Alias 업데이트 완료: {} -> {}", aliasName, newIndexName);
      }

    } catch (Exception e) {
      throw new RuntimeException("Elasticsearch alias 변경 실패", e);
    }
  }

  private void validateIndexName(String indexName) {
    if (indexName == null || indexName.trim().isEmpty()) {
      throw new IllegalArgumentException("인덱스 이름이 비어있습니다");
    }
  }
}
