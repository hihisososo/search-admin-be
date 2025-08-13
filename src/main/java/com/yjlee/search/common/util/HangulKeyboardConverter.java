package com.yjlee.search.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HangulKeyboardConverter {

  private HangulKeyboardConverter() {}

  private static final Map<Character, Character> ENG_TO_JAMO = new HashMap<>();

  private static final char[] CHOSEONG = {
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
  };
  private static final char[] JUNGSEONG = {
    'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ',
    'ㅢ', 'ㅣ'
  };
  private static final char[] JONGSEONG = {
    '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ',
    'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
  };

  static {
    String eng = "qwertyuiopasdfghjklzxcvbnm";
    char[] jamo = {
      'ㅂ', 'ㅈ', 'ㄷ', 'ㄱ', 'ㅅ', 'ㅛ', 'ㅕ', 'ㅑ', 'ㅐ', 'ㅔ', 'ㅁ', 'ㄴ', 'ㅇ', 'ㄹ', 'ㅎ', 'ㅗ', 'ㅓ', 'ㅏ', 'ㅣ',
      'ㅋ', 'ㅌ', 'ㅊ', 'ㅍ', 'ㅠ', 'ㅜ', 'ㅡ'
    };
    for (int i = 0; i < eng.length(); i++) {
      ENG_TO_JAMO.put(eng.charAt(i), jamo[i]);
    }
  }

  public static String engToKor(String input) {
    if (input == null || input.isBlank()) return input;
    String s = input.toLowerCase();
    List<Character> jamoSeq = new ArrayList<>();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      Character jamo = ENG_TO_JAMO.get(c);
      if (jamo != null) {
        jamoSeq.add(jamo);
      } else if (Character.isWhitespace(c)) {
        jamoSeq.add(' ');
      }
    }
    return compose(jamoSeq);
  }

  private static boolean isVowel(char jamo) {
    for (char v : JUNGSEONG) if (v == jamo) return true;
    return false;
  }

  private static int indexOf(char[] arr, char ch) {
    for (int i = 0; i < arr.length; i++) if (arr[i] == ch) return i;
    return -1;
  }

  private static String compose(List<Character> seq) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < seq.size()) {
      char cur = seq.get(i);
      if (cur == ' ') {
        out.append(' ');
        i++;
        continue;
      }

      char choCandidate = cur;
      char jungCandidate = 0;
      // 종성은 조합 계산에서만 사용

      // 보조: 모음만 오면 초성은 ㅇ으로
      if (isVowel(choCandidate)) {
        choCandidate = 'ㅇ';
      } else if (i + 1 < seq.size() && isVowel(seq.get(i + 1))) {
        // 정상 (초성+중성)
      } else {
        // 자음 단독 처리: 그대로 추가하고 진행
        out.append(choCandidate);
        i++;
        continue;
      }

      // 중성 확보
      if (i + 1 < seq.size() && isVowel(seq.get(i + 1))) {
        jungCandidate = seq.get(i + 1);
      }

      int choIdx = indexOf(CHOSEONG, choCandidate);
      int jungIdx = indexOf(JUNGSEONG, jungCandidate);
      int jongIdx = 0;

      // 종성 후보 (다음이 자음이고, 그 다음이 모음이 아니면 종성)
      if (i + 2 < seq.size()) {
        char next2 = seq.get(i + 2);
        if (!isVowel(next2) && next2 != ' ') {
          int cand = indexOf(JONGSEONG, next2);
          if (cand >= 0) jongIdx = cand;
        }
      }

      if (choIdx >= 0 && jungIdx >= 0) {
        int syllable = 0xAC00 + (choIdx * 21 + jungIdx) * 28 + jongIdx;
        out.append((char) syllable);
        i += 2 + (jongIdx > 0 ? 1 : 0);
      } else {
        // 조합 불가 시 개별 출력
        out.append(choCandidate);
        if (jungCandidate != 0) out.append(jungCandidate);
        i += (jungCandidate != 0 ? 2 : 1);
      }
    }
    return out.toString().trim();
  }
}
