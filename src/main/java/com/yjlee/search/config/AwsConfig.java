package com.yjlee.search.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

  private final AwsProperties awsProperties;

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    log.info("AWS 자격 증명 제공자 초기화 중...");

    // IAM 역할 우선 사용, 환경변수는 백업
    if (awsProperties.getAccessKey() != null && awsProperties.getSecretKey() != null) {
      log.info("환경변수에서 AWS 자격 증명 사용");
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(awsProperties.getAccessKey(), awsProperties.getSecretKey()));
    } else {
      log.info("기본 자격 증명 체인 사용 (IAM 역할, AWS CLI 등)");
      return DefaultCredentialsProvider.create();
    }
  }

  @Bean
  public SsmClient ssmClient(AwsCredentialsProvider credentialsProvider) {
    log.info("SSM 클라이언트 초기화 - 리전: {}", awsProperties.getRegion());

    return SsmClient.builder()
        .region(Region.of(awsProperties.getRegion()))
        .credentialsProvider(credentialsProvider)
        .build();
  }

  @Getter
  @Setter
  @ConfigurationProperties(prefix = "app.aws")
  @Configuration
  public static class AwsProperties {
    private String accessKey;
    private String secretKey;
    private String region = "ap-northeast-2";
    private Ec2Properties ec2 = new Ec2Properties();

    @Getter
    @Setter
    public static class Ec2Properties {
      private String[] instanceIds = new String[0];
    }
  }
}
