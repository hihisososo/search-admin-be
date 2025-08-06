package com.yjlee.search.common.util;

public class KoreanTextUtils {

  private static final int CHOSUNG_BASE = 0x1100;
  private static final int JUNGSUNG_BASE = 0x1161;
  private static final int JONGSUNG_BASE = 0x11A7;
  private static final int HANGUL_BASE = 0xAC00;
  private static final int HANGUL_END = 0xD7A3;

  private static final char[] CHOSUNG_LIST = {
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 
    'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
  };

  private static final char[] JUNGSUNG_LIST = {
    'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 
    'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 
    'ㅡ', 'ㅢ', 'ㅣ'
  };

  private static final char[] JONGSUNG_LIST = {
    '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 
    'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 
    'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
  };

  /**
   * 한글 텍스트를 자소 분해
   */
  public static String decomposeHangul(String text) {
    if (text == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();
    
    for (char ch : text.toCharArray()) {
      if (ch >= HANGUL_BASE && ch <= HANGUL_END) {
        int code = ch - HANGUL_BASE;
        int chosungIndex = code / (21 * 28);
        int jungsungIndex = (code % (21 * 28)) / 28;
        int jongsungIndex = code % 28;

        result.append(CHOSUNG_LIST[chosungIndex]);
        result.append(JUNGSUNG_LIST[jungsungIndex]);
        if (jongsungIndex > 0) {
          result.append(JONGSUNG_LIST[jongsungIndex]);
        }
      } else {
        result.append(ch);
      }
    }
    
    return result.toString();
  }

  /**
   * 한글 텍스트에서 초성만 추출 (한글이 아닌 문자는 그대로 유지)
   */
  public static String extractChosung(String text) {
    if (text == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();
    
    for (char ch : text.toCharArray()) {
      if (ch >= HANGUL_BASE && ch <= HANGUL_END) {
        int code = ch - HANGUL_BASE;
        int chosungIndex = code / (21 * 28);
        result.append(CHOSUNG_LIST[chosungIndex]);
      } else {
        result.append(ch);
      }
    }
    
    return result.toString();
  }

  private static boolean isChosung(char ch) {
    for (char chosung : CHOSUNG_LIST) {
      if (ch == chosung) {
        return true;
      }
    }
    return false;
  }
}