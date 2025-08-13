package com.yjlee.search.common.util;

public final class EditDistance {

  private EditDistance() {}

  public static int levenshtein(String a, String b, int maxDistance) {
    if (a == null || b == null) return Integer.MAX_VALUE;
    int n = a.length();
    int m = b.length();
    if (Math.abs(n - m) > maxDistance) return maxDistance + 1;

    int[] prev = new int[m + 1];
    int[] curr = new int[m + 1];

    for (int j = 0; j <= m; j++) prev[j] = j;

    for (int i = 1; i <= n; i++) {
      curr[0] = i;
      int best = curr[0];
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= m; j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
        if (curr[j] < best) best = curr[j];
      }
      if (best > maxDistance) return best;
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }

    return prev[m];
  }
}
