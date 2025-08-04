FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

# APM Agent 다운로드
RUN mkdir -p /app/apm-agent && \
    curl -L -o /app/apm-agent/elastic-apm-agent.jar \
    https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/1.46.0/elastic-apm-agent-1.46.0.jar

WORKDIR /app

COPY build/libs/*.jar app.jar

# 실행 스크립트 생성
RUN echo '#!/bin/sh' > /app/entrypoint.sh && \
    echo 'if [ "${ELASTIC_APM_ENABLED}" = "true" ]; then' >> /app/entrypoint.sh && \
    echo '    echo "Running with APM Agent..."' >> /app/entrypoint.sh && \
    echo '    exec java -javaagent:/app/apm-agent/elastic-apm-agent.jar \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.service_name=${SPRING_APPLICATION_NAME:-search-admin-be} \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.server_url=${ELASTIC_APM_SERVER_URL} \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.secret_token=${ELASTIC_APM_SECRET_TOKEN} \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.environment=${SPRING_PROFILES_ACTIVE:-prod} \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.log_level=INFO \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.transaction_sample_rate=1.0 \' >> /app/entrypoint.sh && \
    echo '        -Delastic.apm.capture_body=off \' >> /app/entrypoint.sh && \
    echo '        -jar /app/app.jar' >> /app/entrypoint.sh && \
    echo 'else' >> /app/entrypoint.sh && \
    echo '    echo "Running without APM Agent"' >> /app/entrypoint.sh && \
    echo '    exec java -jar /app/app.jar' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]