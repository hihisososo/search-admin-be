#!/bin/bash

# Spring Boot with Elastic APM Agent 실행 스크립트

# APM Agent 경로
APM_AGENT_PATH="./apm-agent/elastic-apm-agent.jar"

# APM이 활성화되어 있는지 확인
if [ "${ELASTIC_APM_ENABLED}" = "true" ]; then
    echo "Starting with Elastic APM Agent..."
    
    # APM Agent가 존재하는지 확인
    if [ ! -f "${APM_AGENT_PATH}" ]; then
        echo "APM Agent not found. Running download script..."
        ./scripts/download-apm-agent.sh
    fi
    
    # JVM 옵션 설정
    JAVA_OPTS="${JAVA_OPTS} -javaagent:${APM_AGENT_PATH}"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.service_name=${SPRING_APPLICATION_NAME:-search-admin-be}"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.server_url=${ELASTIC_APM_SERVER_URL}"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.secret_token=${ELASTIC_APM_SECRET_TOKEN}"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.environment=${SPRING_PROFILES_ACTIVE:-prod}"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.log_level=INFO"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.transaction_sample_rate=0.1"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.capture_body=off"
    JAVA_OPTS="${JAVA_OPTS} -Delastic.apm.sanitize_field_names=*password*,*secret*,*token*,*key*"
else
    echo "Starting without APM Agent (ELASTIC_APM_ENABLED is not true)"
fi

# Spring Boot 실행
exec java ${JAVA_OPTS} -jar build/libs/search-admin-be-*.jar