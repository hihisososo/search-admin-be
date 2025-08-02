package com.yjlee.search.search.service.typo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TypoCorrectionServiceTest {

  @Mock private TypoCorrectionDictionaryService dictionaryService;

  @InjectMocks private TypoCorrectionService typoCorrectionService;

  private void setupMockDictionary() {
    var dict1 =
        TypoCorrectionDictionaryListResponse.builder()
            .keyword("ipone")
            .correctedWord("iphone")
            .build();

    var dict2 =
        TypoCorrectionDictionaryListResponse.builder()
            .keyword("samsnug")
            .correctedWord("samsung")
            .build();

    var dict3 =
        TypoCorrectionDictionaryListResponse.builder().keyword("삼송").correctedWord("삼성").build();

    var dict4 =
        TypoCorrectionDictionaryListResponse.builder().keyword("겔럭시").correctedWord("갤럭시").build();

    var page = new PageImpl<>(List.of(dict1, dict2, dict3, dict4), PageRequest.of(0, 1000), 4);

    PageResponse<TypoCorrectionDictionaryListResponse> pageResponse = PageResponse.from(page);

    when(dictionaryService.getTypoCorrectionDictionaries(1, 1000, null, "keyword", "asc", null))
        .thenReturn(pageResponse);
  }

  @Test
  @DisplayName("null 또는 빈 쿼리는 그대로 반환")
  void testEmptyQuery() {
    assertThat(typoCorrectionService.applyTypoCorrection(null)).isNull();
    assertThat(typoCorrectionService.applyTypoCorrection("")).isEmpty();
    assertThat(typoCorrectionService.applyTypoCorrection("   ")).isEqualTo("   ");
  }

  @Test
  @DisplayName("대소문자 구분하여 오타 교정")
  void testCaseSensitiveCorrection() {
    setupMockDictionary();

    String result1 = typoCorrectionService.applyTypoCorrection("ipone");
    String result2 = typoCorrectionService.applyTypoCorrection("IPONE");
    String result3 = typoCorrectionService.applyTypoCorrection("samsnug");
    String result4 = typoCorrectionService.applyTypoCorrection("SamsNug");

    assertThat(result1).isEqualTo("iphone");
    assertThat(result2).isEqualTo("IPONE"); // 대소문자가 다르면 교정 안됨
    assertThat(result3).isEqualTo("samsung");
    assertThat(result4).isEqualTo("SamsNug"); // 대소문자가 다르면 교정 안됨
  }

  @Test
  @DisplayName("복잡한 문장에서 여러 오타 교정")
  void testComplexSentenceCorrection() {
    setupMockDictionary();

    String result = typoCorrectionService.applyTypoCorrection("삼송 겔럭시 스마트폰 구매");

    assertThat(result).isEqualTo("삼성 갤럭시 스마트폰 구매");
  }
}
