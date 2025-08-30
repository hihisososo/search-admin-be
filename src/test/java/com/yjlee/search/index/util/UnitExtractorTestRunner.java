package com.yjlee.search.index.util;

import java.util.List;

public class UnitExtractorTestRunner {
  public static void main(String[] args) {
    System.out.println("=== 단위 증강 테스트 ===\n");

    // 1L -> ml 변환 테스트
    System.out.println("테스트 1: '서울우유 1L'");
    List<String> result1 = UnitExtractor.extractUnitsForIndexing("서울우유 1L");
    System.out.println("결과: " + result1);
    System.out.println("예상: [1l, 1리터, 1ℓ, 1000ml, 1000밀리리터, 1000cc, ...]\n");

    // 500ml -> L 변환 테스트
    System.out.println("테스트 2: '생수 500ml'");
    List<String> result2 = UnitExtractor.extractUnitsForIndexing("생수 500ml");
    System.out.println("결과: " + result2);
    System.out.println("예상: [500ml, 500밀리리터, 500cc, 0.5l, 0.5리터, ...]\n");

    // 1kg -> g 변환 테스트
    System.out.println("테스트 3: '설탕 1kg'");
    List<String> result3 = UnitExtractor.extractUnitsForIndexing("설탕 1kg");
    System.out.println("결과: " + result3);
    System.out.println("예상: [1kg, 1킬로그램, 1000g, 1000그램, ...]\n");

    // 500g -> kg 변환 테스트
    System.out.println("테스트 4: '소금 500g'");
    List<String> result4 = UnitExtractor.extractUnitsForIndexing("소금 500g");
    System.out.println("결과: " + result4);
    System.out.println("예상: [500g, 500그램, 0.5kg, 0.5킬로그램, ...]\n");

    // 2m -> cm, mm 변환 테스트
    System.out.println("테스트 5: '길이 2m'");
    List<String> result5 = UnitExtractor.extractUnitsForIndexing("길이 2m");
    System.out.println("결과: " + result5);
    System.out.println("예상: [2m, 2미터, 200cm, 200센티미터, 2000mm, ...]\n");

    // 27inch -> cm 변환 테스트
    System.out.println("테스트 6: '모니터 27인치'");
    List<String> result6 = UnitExtractor.extractUnitsForIndexing("모니터 27인치");
    System.out.println("결과: " + result6);
    System.out.println("예상: [27인치, 27inch, 68.58cm, 68.58센티미터, ...]\n");

    // GB -> MB 변환 테스트
    System.out.println("테스트 7: '메모리 8GB'");
    List<String> result7 = UnitExtractor.extractUnitsForIndexing("메모리 8GB");
    System.out.println("결과: " + result7);
    System.out.println("예상: [8gb, 8기가바이트, 8192mb, 8192메가바이트, ...]\n");

    // 복합 단위 테스트
    System.out.println("테스트 8: '크기 10x20cm'");
    List<String> result8 = UnitExtractor.extractUnitsForIndexing("크기 10x20cm");
    System.out.println("결과: " + result8);
    System.out.println("예상: [10x20cm, 10cm, 20cm, 100mm, 200mm, 0.1m, 0.2m, ...]\n");

    // 개수 단위 테스트
    System.out.println("테스트 9: '사과 10개'");
    List<String> result9 = UnitExtractor.extractUnitsForIndexing("사과 10개");
    System.out.println("결과: " + result9);
    System.out.println("예상: [10개, 10ea, 10pcs, 10piece, ...]\n");

    // 병 단위 테스트
    System.out.println("테스트 10: '맥주 6병'");
    List<String> result10 = UnitExtractor.extractUnitsForIndexing("맥주 6병");
    System.out.println("결과: " + result10);
    System.out.println("예상: [6병, 6btl, 6bottle, ...]\n");

    // 여러 단위 동시 테스트
    System.out.println("테스트 11: '서울우유 1L 10개입'");
    List<String> result11 = UnitExtractor.extractUnitsForIndexing("서울우유 1L 10개입");
    System.out.println("결과: " + result11);
    System.out.println("예상: [1l, 1000ml, 10개입, 10개들이, ...]\n");

    // 검색용 추출 (증강 없음) 테스트
    System.out.println("발사믹크림500밀리리터");
    List<String> result12 = UnitExtractor.extractUnitsForSearch("발사믹크림500밀리리터");
    System.out.println("결과: " + result12);

    System.out.println("=== 테스트 완료 ===");
  }
}
