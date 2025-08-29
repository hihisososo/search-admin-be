package com.yjlee.search.index.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnitExtractorTest {

  @Test
  void 리터_밀리리터_증강() {
    String text = "서울우유 1L";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains("1l", "1리터", "1ℓ", "1000ml", "1000밀리리터", "1000밀리", "1000cc", "1000시시");
  }

  @Test
  void 밀리리터_리터_증강() {
    String text = "생수 500ml";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains("500ml", "500밀리리터", "500밀리", "500cc", "500시시", "0.5l", "0.5리터", "0.5ℓ");
  }

  @Test
  void 킬로그램_그램_증강() {
    String text = "설탕 1kg";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("1kg", "1킬로그램", "1킬로", "1키로", "1000g", "1000그램", "1000그람");
  }

  @Test
  void 그램_킬로그램_밀리그램_증강() {
    String text = "소금 500g";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("500g", "500그램", "500그람", "0.5kg", "0.5킬로그램", "0.5킬로", "0.5키로");

    assertThat(augmented).doesNotContain("500000mg"); // 500g는 너무 커서 mg로 변환 안함
  }

  @Test
  void 작은_그램_밀리그램_증강() {
    String text = "약 5g";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("5g", "5그램", "5그람", "5000mg", "5000밀리그램");
  }

  @Test
  void 미터_센티미터_밀리미터_증강() {
    String text = "길이 2m";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains(
            "2m", "2미터", "200cm", "200센티미터", "200센티", "200센치", "2000mm", "2000밀리미터", "2000밀리");
  }

  @Test
  void 센티미터_미터_밀리미터_증강() {
    String text = "너비 50cm";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains("50cm", "50센티미터", "50센티", "50센치", "0.5m", "0.5미터", "500mm", "500밀리미터", "500밀리");
  }

  @Test
  void 인치_센티미터_증강() {
    String text = "모니터 27인치";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains("27인치", "27inch", "27in", "27\"", "68.58cm", "68.58센티미터", "68.58센티", "68.58센치");
  }

  @Test
  void 기가바이트_메가바이트_증강() {
    String text = "메모리 8GB";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains(
            "8gb", "8기가바이트",
            "8192mb", "8192메가바이트");
  }

  @Test
  void 메가바이트_기가바이트_킬로바이트_증강() {
    String text = "용량 512MB";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains(
            "512mb", "512메가바이트",
            "0.5gb", "0.5기가바이트",
            "524288kb", "524288킬로바이트");
  }

  @Test
  void 개수_단위_증강() {
    String text = "사과10개";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("10개", "10ea");
  }

  @Test
  void 박스_팩_세트_증강() {
    String text = "라면 1box";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("1box", "1박스", "1boxes");
  }

  @Test
  void 복합_단위_증강() {
    String text = "크기 10x20cm";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains(
            "10x20cm", "10cm", "10센티미터", "10센티", "10센치", "20cm", "20센티미터", "20센티", "20센치", "100mm",
            "100밀리미터", "100밀리", "200mm", "200밀리미터", "200밀리", "0.1m", "0.1미터", "0.2m", "0.2미터");
  }

  @Test
  void 여러_단위_동시_증강() {
    String text = "서울우유 1L 10개입";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains(
            "1l", "1리터", "1ℓ", "1000ml", "1000밀리리터", "1000밀리", "1000cc", "1000시시", "10개입", "10개들이");
  }

  @Test
  void 증강없는_검색용_추출() {
    String text = "서울우유 1L";

    List<String> notAugmented = UnitExtractor.extractUnitsForSearch(text);

    assertThat(notAugmented).containsExactlyInAnyOrder("1l");
    assertThat(notAugmented).doesNotContain("1000ml", "1리터");
  }

  @Test
  void 소수점_단위_증강() {
    String text = "1.5L";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented)
        .contains("1.5l", "1.5리터", "1.5ℓ", "1500ml", "1500밀리리터", "1500밀리", "1500cc", "1500시시");
  }

  @Test
  void 한글_단위_증강() {
    String text = "5장 10봉";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("5장", "5매", "10봉", "10봉지", "10포");
  }

  @Test
  void 켤레_족_쌍_증강() {
    String text = "양말 3켤레";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("3켤레", "3족", "3쌍");
  }

  @Test
  void 병_캔_증강() {
    String text = "맥주 6병";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("6병", "6btl", "6bottle", "6bottles");
  }

  @Test
  void 전력_단위_증강() {
    String text = "소비전력 100W";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("100w", "100와트");
  }

  @Test
  void 주파수_단위_증강() {
    String text = "주파수 2.4GHz";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("2.4ghz", "2.4기가헤르츠");
  }

  @Test
  void 배터리_용량_증강() {
    String text = "배터리 3000mAh";

    List<String> augmented = UnitExtractor.extractUnitsForIndexing(text);

    assertThat(augmented).contains("3000mah", "3000밀리암페어시");
  }
}
