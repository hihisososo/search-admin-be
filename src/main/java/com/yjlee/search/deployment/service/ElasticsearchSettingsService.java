package com.yjlee.search.deployment.service;

import com.yjlee.search.index.provider.IndexNameProvider;
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
public class ElasticsearchSettingsService {

  private static final String MAPPING_PATH_PREFIX = "classpath:elasticsearch/";
  private static final String PRODUCT_SETTINGS_FILE = "product-settings.json";
  private static final String AUTOCOMPLETE_SETTINGS_FILE = "autocomplete-settings.json";

  private final ResourceLoader resourceLoader;
  private final IndexNameProvider indexNameProvider;

  public String createProductIndexSettings(String version, String synonymSetName) {
    String settingsTemplate = loadResourceFile(MAPPING_PATH_PREFIX + PRODUCT_SETTINGS_FILE);

    return settingsTemplate
        .replace("{USER_DICT_PATH}", indexNameProvider.getUserDictPath(version))
        .replace("{STOPWORD_DICT_PATH}", indexNameProvider.getStopwordDictPath(version))
        .replace("{UNIT_DICT_PATH}", indexNameProvider.getUnitDictPath(version))
        .replace("{SYNONYM_SET_NAME}", synonymSetName);
  }

  public String createAutocompleteIndexSettings(String version) {
    String settingsTemplate = loadResourceFile(MAPPING_PATH_PREFIX + AUTOCOMPLETE_SETTINGS_FILE);
    return settingsTemplate.replace("{USER_DICT_PATH}", indexNameProvider.getUserDictPath(version));
  }

  private String loadResourceFile(String path) {
    Resource resource = resourceLoader.getResource(path);
    try {
      return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("리소스 로드 중 에러", e);
    }
  }
}
