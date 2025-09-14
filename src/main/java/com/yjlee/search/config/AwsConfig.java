package com.yjlee.search.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

@Slf4j
@Configuration
public class AwsConfig {

  @Value("${app.aws.access-key}")
  private String accessKey;

  @Value("${app.aws.secret-key}")
  private String secretKey;

  @Value("${app.aws.region:ap-northeast-2}")
  private String region;

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    log.info("AWS 자격 증명 제공자 초기화 중...");
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey));
  }

  @Bean
  public SsmClient ssmClient(AwsCredentialsProvider credentialsProvider) {
    log.info("SSM 클라이언트 초기화 - 리전: {}", region);

    return SsmClient.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .build();
  }
}
