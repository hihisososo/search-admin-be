package com.yjlee.search.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class S3Config {

  private final S3Properties s3Properties;

  @Bean
  public S3Client s3Client() {
    log.info(
        "Initializing S3 client for region: {}, bucket: {}",
        s3Properties.getRegion(),
        s3Properties.getBucket());

    AwsCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey()));

    return S3Client.builder()
        .region(Region.of(s3Properties.getRegion()))
        .credentialsProvider(credentialsProvider)
        .build();
  }

  @Getter
  @Setter
  @ConfigurationProperties(prefix = "app.s3")
  @Configuration
  public static class S3Properties {
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private String baseUrl;
  }
}
