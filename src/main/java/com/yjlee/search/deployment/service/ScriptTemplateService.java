package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.constant.DeploymentConstants;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ScriptTemplateService {

  public String createDictionaryDeployScript(
      String tempFilePath, String targetPath, String dictionaryType) {

    return String.format(
        """
        #!/bin/bash
        set -e

        echo "============================================"
        echo "%s 배포 시작"
        echo "============================================"

        # 1. 기존 파일 백업
        if [ -f "%s" ]; then
            BACKUP_FILE="%s.backup.$(date +%%Y%%m%%d_%%H%%M%%S)"
            %s
            echo "기존 파일 백업 완료: $BACKUP_FILE"
        fi

        # 2. 새 파일 복사
        %s
        echo "새 파일 복사 완료"

        # 3. 권한 설정
        %s%s
        %s%s
        echo "파일 권한 설정 완료"

        # 4. Elasticsearch analyzer 새로고침
        sleep 2
        RESPONSE=$(%s)
        echo "Analyzer 새로고침 응답: $RESPONSE"

        # 5. 임시 파일 삭제
        rm -f %s

        echo "============================================"
        echo "%s 배포 완료"
        echo "============================================"
        """,
        dictionaryType,
        targetPath,
        targetPath,
        String.format(
            DeploymentConstants.Commands.BACKUP_COMMAND_FORMAT,
            targetPath,
            targetPath,
            "$(date +%Y%m%d_%H%M%S)"),
        String.format(DeploymentConstants.Commands.COPY_COMMAND_FORMAT, tempFilePath, targetPath),
        DeploymentConstants.Commands.CHMOD_COMMAND,
        targetPath,
        DeploymentConstants.Commands.CHOWN_COMMAND,
        targetPath,
        DeploymentConstants.Commands.REFRESH_SEARCH_ANALYZER,
        tempFilePath,
        dictionaryType);
  }

  public String createFileUploadScript(List<String> lines, String tempFilePath) {
    String content =
        lines.stream().map(line -> line.replace("'", "'\\''")).collect(Collectors.joining("\\n"));

    return String.format("echo '%s' > %s", content, tempFilePath);
  }

  public String createIndexRefreshScript(String indexName) {
    return String.format(
        """
        curl -X POST 'http://localhost:9200/%s/_refresh' \
        -H 'Content-Type: application/json'
        """,
        indexName);
  }

  public String createAliasUpdateScript(String aliasName, String oldIndex, String newIndex) {
    return String.format(
        """
        curl -X POST 'http://localhost:9200/_aliases' \
        -H 'Content-Type: application/json' \
        -d '{
          "actions": [
            { "remove": { "index": "%s", "alias": "%s" } },
            { "add": { "index": "%s", "alias": "%s" } }
          ]
        }'
        """,
        oldIndex, aliasName, newIndex, aliasName);
  }

  public String createIndexDeleteScript(String indexName) {
    return String.format(
        """
        curl -X DELETE 'http://localhost:9200/%s'
        """, indexName);
  }
}
