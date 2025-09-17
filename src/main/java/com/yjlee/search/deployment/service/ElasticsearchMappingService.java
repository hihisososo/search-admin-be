package com.yjlee.search.deployment.service;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchMappingService {

  private static final String MAPPING_PATH_PREFIX = "classpath:elasticsearch/";
  private static final String PRODUCT_MAPPING_FILE = "product-mapping.json";
  private static final String AUTOCOMPLETE_MAPPING_FILE = "autocomplete-mapping.json";

  private final ResourceLoader resourceLoader;

  public String loadProductMapping() {
    return loadResourceFile(MAPPING_PATH_PREFIX + PRODUCT_MAPPING_FILE);
  }

  public String loadAutocompleteMapping() {
    return loadResourceFile(MAPPING_PATH_PREFIX + AUTOCOMPLETE_MAPPING_FILE);
  }

  private String loadResourceFile(String path) {
    Resource resource = resourceLoader.getResource(path);
    try {
      return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("리소스 파일 로드 중 에러 발생", e);
    }
  }
}
