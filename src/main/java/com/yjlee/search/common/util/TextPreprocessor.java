package com.yjlee.search.common.util;

import static java.text.Normalizer.Form;

import java.text.Normalizer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextPreprocessor {

  // 의미있는 특수문자를 보존: /, -, +, &
  private static final Pattern SPECIAL_CHARS_PATTERN =
      Pattern.compile("[^\\p{L}\\p{N}\\s\\.\\-/+&]");

  // 천단위 구분자 패턴 - 숫자 3자리 단위로 구분
  private static final Pattern THOUSAND_SEPARATOR_PATTERN =
      Pattern.compile("(\\d),(?=\\d{3}(?:,|\\D|$))");

  public static String preprocess(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    text = removeThousandSeparators(text);
    return normalizeUnicode(toLowerCase(cleanSpecialChars(text)));
  }

  private static String cleanSpecialChars(String text) {
    return SPECIAL_CHARS_PATTERN
        .matcher(text.trim())
        .replaceAll(" ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String toLowerCase(String text) {
    return text.toLowerCase();
  }

  private static String normalizeUnicode(String text) {
    return Normalizer.normalize(text, Form.NFC);
  }

  private static String removeThousandSeparators(String text) {
    String result = text;
    String prev;
    do {
      prev = result;
      result = THOUSAND_SEPARATOR_PATTERN.matcher(result).replaceAll("$1");
    } while (!result.equals(prev));
    return result;
  }
}
