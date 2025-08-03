package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScriptTemplateServiceTest {

  private ScriptTemplateService scriptTemplateService;

  @BeforeEach
  void setUp() {
    scriptTemplateService = new ScriptTemplateService();
  }

  @Test
  @DisplayName("사전 배포 스크립트 생성")
  void createDictionaryDeployScript() {
    // Given
    String tempFilePath = "/tmp/test.txt";
    String targetPath = "/usr/share/elasticsearch/config/analysis/userdict_ko.txt";
    String dictionaryType = "사용자 사전";

    // When
    String script =
        scriptTemplateService.createDictionaryDeployScript(
            tempFilePath, targetPath, dictionaryType);

    // Then
    assertThat(script).contains("#!/bin/bash");
    assertThat(script).contains("set -e");
    assertThat(script).contains(dictionaryType + " 배포 시작");
    assertThat(script).contains("기존 파일 백업");
    assertThat(script).contains("새 파일 복사");
    assertThat(script).contains("파일 권한 설정");
    assertThat(script).contains("Analyzer 새로고침");
    assertThat(script).contains(dictionaryType + " 배포 완료");
  }

  @Test
  @DisplayName("파일 업로드 스크립트 생성")
  void createFileUploadScript() {
    // Given
    List<String> lines = Arrays.asList("line1", "line2 with 'quotes'", "line3");
    String tempFilePath = "/tmp/upload.txt";

    // When
    String script = scriptTemplateService.createFileUploadScript(lines, tempFilePath);

    // Then
    assertThat(script).startsWith("echo '");
    assertThat(script).endsWith("' > " + tempFilePath);
    assertThat(script).contains("line1\\nline2 with '\\''quotes'\\''\\nline3");
  }

  @Test
  @DisplayName("인덱스 새로고침 스크립트 생성")
  void createIndexRefreshScript() {
    // Given
    String indexName = "products_search_v1";

    // When
    String script = scriptTemplateService.createIndexRefreshScript(indexName);

    // Then
    assertThat(script).contains("curl -X POST");
    assertThat(script).contains("http://localhost:9200/" + indexName + "/_refresh");
    assertThat(script).contains("-H 'Content-Type: application/json'");
  }

  @Test
  @DisplayName("Alias 업데이트 스크립트 생성")
  void createAliasUpdateScript() {
    // Given
    String aliasName = "products_search";
    String oldIndex = "products_search_v1";
    String newIndex = "products_search_v2";

    // When
    String script = scriptTemplateService.createAliasUpdateScript(aliasName, oldIndex, newIndex);

    // Then
    assertThat(script).contains("curl -X POST");
    assertThat(script).contains("http://localhost:9200/_aliases");
    assertThat(script).contains("remove");
    assertThat(script).contains(oldIndex);
    assertThat(script).contains("add");
    assertThat(script).contains(newIndex);
    assertThat(script).contains(aliasName);
  }

  @Test
  @DisplayName("인덱스 삭제 스크립트 생성")
  void createIndexDeleteScript() {
    // Given
    String indexName = "products_search_old";

    // When
    String script = scriptTemplateService.createIndexDeleteScript(indexName);

    // Then
    assertThat(script).contains("curl -X DELETE");
    assertThat(script).contains("http://localhost:9200/" + indexName);
  }
}
