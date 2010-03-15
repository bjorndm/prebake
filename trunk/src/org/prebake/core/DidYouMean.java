package org.prebake.core;

public final class DidYouMean {
  private DidYouMean() {}  // not instantiable


  public static void toMessageQueue(
      String problem, String given, MessageQueue mq, String... options) {
    mq.getMessages().add(toMessage(problem, given, options));
  }

  public static String toMessage(
      String problem, String given, String... options) {
    int bestOptionDist = editDistance(given, options[0]);
    String bestOption = options[0];
    for (int i = 0, n = options.length; i < n; ++i) {
      String option = options[i];
      int ed = editDistance(given, option);
      if (ed < bestOptionDist) {
        bestOption = option;
        bestOptionDist = ed;
      }
    }
    return problem + ". Did you mean \"" + bestOption + "\"?";
  }

  /**
   * The edit distance between two strings.
   *
   * <p>
   * Adapted from <a href="http://www.merriampark.com/ldjava.htm"
   * >http://www.merriampark.com/ldjava.htm</a></p>
   */
  private static int editDistance(String a, String b) {
    int n = a.length();
    int m = b.length();

    if (n == 0) {
      return m;
    } else if (m == 0) {
      return n;
    }

    if (n > m) {
      // Swap the input strings to consume less memory
      String t = a;
      a = b;
      b = t;
      int i = n;
      n = m;
      m = i;
    }

    int p[] = new int[n + 1]; // 'previous' cost array, horizontally
    int d[] = new int[n + 1]; // cost array, horizontally
    int _d[]; // placeholder to assist in swapping p and d

    for (int i = 0; i <= n; i++) { p[i] = i; }

    for (int j = 1; j <= m; j++) {
      char b_j = b.charAt(j - 1);
      d[0] = j;

      for (int i = 1; i <= n; i++) {
        int cost = a.charAt(i - 1) == b_j ? 0 : 1;
        // minimum of cell to the left+1, to the top+1,
        // diagonally left and up + cost
        d[i] = min3(d[i - 1] + 1, p[i] + 1,  p[i - 1] + cost);
      }

      // copy current distance counts to 'previous row' distance counts
      _d = p;
      p = d;
      d = _d;
    }

    // our last action in the above loop was to switch d and p, so p now
    // actually has the most recent cost counts
    return p[n];
  }

  private static int min3(int a, int b, int c) {
    int min2 = a < b ? a : b;
    return min2 < c ? min2 : c;
  }
}
