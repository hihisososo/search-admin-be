package com.yjlee.search.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Slf4j
@Component
public class PromptTemplateLoader {

  public String loadTemplate(String templatePath) {
    try {
      ClassPathResource resource = new ClassPathResource("prompts/" + templatePath);
      return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("❌ 프롬프트 템플릿 로드 실패: {}", templatePath, e);
      return "";
    }
  }

  public String loadTemplate(String templatePath, Map<String, String> variables) {
    String template = loadTemplate(templatePath);
    if (template.isEmpty()) {
      return "";
    }

    String result = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String placeholder = "{" + entry.getKey() + "}";
      result = result.replace(placeholder, entry.getValue());
    }

    return result;
  }
}
