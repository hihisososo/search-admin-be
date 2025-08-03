#!/bin/bash

# Elastic APM Agent 다운로드 스크립트
APM_AGENT_VERSION="1.46.0"
APM_AGENT_FILE="elastic-apm-agent-${APM_AGENT_VERSION}.jar"
DOWNLOAD_URL="https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/${APM_AGENT_VERSION}/${APM_AGENT_FILE}"

# 에이전트 디렉토리 생성
mkdir -p apm-agent

# 이미 존재하는지 확인
if [ -f "apm-agent/${APM_AGENT_FILE}" ]; then
    echo "APM Agent already exists: apm-agent/${APM_AGENT_FILE}"
else
    echo "Downloading Elastic APM Agent ${APM_AGENT_VERSION}..."
    curl -L -o "apm-agent/${APM_AGENT_FILE}" "${DOWNLOAD_URL}"
    
    if [ $? -eq 0 ]; then
        echo "Download completed: apm-agent/${APM_AGENT_FILE}"
    else
        echo "Download failed!"
        exit 1
    fi
fi

# 심볼릭 링크 생성 (버전 관리 용이)
ln -sf "${APM_AGENT_FILE}" "apm-agent/elastic-apm-agent.jar"
echo "Created symlink: apm-agent/elastic-apm-agent.jar -> ${APM_AGENT_FILE}"