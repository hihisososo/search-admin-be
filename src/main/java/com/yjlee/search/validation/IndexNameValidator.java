package com.yjlee.search.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IndexNameValidator implements ConstraintValidator<ValidIndexName, String> {

  // Elasticsearch 색인명 규칙에 따른 패턴 (3-50자, 영문소문자/숫자/하이픈/언더바)
  private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,50}$");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.trim().isEmpty()) {
      log.debug("Index name validation passed - null or empty value");
      return true;
    }

    String trimmedValue = value.trim();
    log.debug("Validating index name: {}", trimmedValue);

    // 길이 및 패턴 검증
    if (!INDEX_NAME_PATTERN.matcher(trimmedValue).matches()) {
      log.warn("Index name validation failed - pattern mismatch: {}", trimmedValue);
      return false;
    }

    // 언더바로 시작하는 색인명 차단
    if (trimmedValue.startsWith("_")) {
      log.warn("Index name validation failed - starts with underscore: {}", trimmedValue);
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("색인명은 언더바(_)로 시작할 수 없습니다")
          .addConstraintViolation();
      return false;
    }

    log.debug("Index name validation passed: {}", trimmedValue);
    return true;
  }
}
