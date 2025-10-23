package io.github.pojotools.pojodiff.core.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Glob to regex converter for JSON Pointer paths. Supports '*', '**' and '?' wildcards.
 * Intention-revealing, small helper methods per Uncle Bob's guidance.
 */
public final class GlobPatterns {
  private static final Set<Character> REGEX_META_CHARS =
      Set.of('.', '(', ')', '+', '|', '^', '$', '@', '%', '\\', '{', '}', '[', ']', '#');

  private GlobPatterns() {}

  public static Pattern globToRegex(String glob) {
    StringBuilder regex = new StringBuilder();
    processGlobCharacters(glob.toCharArray(), regex);
    return Pattern.compile(regex.toString());
  }

  private static void processGlobCharacters(char[] chars, StringBuilder out) {
    for (int i = 0; i < chars.length; i++) {
      i = processCharacter(chars, i, out);
    }
  }

  private static int processCharacter(char[] chars, int index, StringBuilder out) {
    char c = chars[index];

    if (isStar(c)) {
      return appendStar(out, chars, index);
    }

    if (isQuestion(c)) {
      appendQuestion(out);
    } else if (isRegexMeta(c)) {
      appendEscaped(out, c);
    } else {
      appendLiteral(out, c);
    }

    return index;
  }

  private static boolean isStar(char c) {
    return c == '*';
  }

  private static boolean isQuestion(char c) {
    return c == '?';
  }

  private static int appendStar(StringBuilder out, char[] chars, int i) {
    if (isDoubleStar(chars, i)) {
      appendDoubleStar(out);
      return i + 1;
    }
    appendSingleStar(out);
    return i;
  }

  private static boolean isDoubleStar(char[] chars, int i) {
    return (i + 1) < chars.length && chars[i + 1] == '*';
  }

  private static void appendDoubleStar(StringBuilder out) {
    out.append(".*");
  }

  private static void appendSingleStar(StringBuilder out) {
    out.append("[^/]*");
  }

  private static void appendQuestion(StringBuilder out) {
    out.append("[^/]");
  }

  private static void appendEscaped(StringBuilder out, char c) {
    out.append("\\").append(c);
  }

  private static void appendLiteral(StringBuilder out, char c) {
    out.append(c);
  }

  private static boolean isRegexMeta(char c) {
    return REGEX_META_CHARS.contains(c);
  }
}
