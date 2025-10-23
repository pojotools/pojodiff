package io.github.pojotools.pojodiff.core.equivalence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.function.BiPredicate;

public final class Equivalences {
  private Equivalences() {}

  public static BiPredicate<JsonNode, JsonNode> caseInsensitive() {
    return (l, r) -> l != null && r != null && l.asText().equalsIgnoreCase(r.asText());
  }

  /**
   * Equal if, after treating punctuation as whitespace and normalizing, both strings match. Keeps
   * only alphanumeric characters with single spaces between words.
   */
  public static BiPredicate<JsonNode, JsonNode> punctuationQuestionEquals() {
    return (l, r) -> {
      if (l == null || r == null || !l.isTextual() || !r.isTextual()) {
        return false;
      }
      return normalizePunctuation(l.asText()).equals(normalizePunctuation(r.asText()));
    };
  }

  private static String normalizePunctuation(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    boolean lastWasSpace = false;
    for (char c : s.toCharArray()) {
      lastWasSpace = appendNormalizedChar(sb, c, lastWasSpace);
    }
    return sb.toString().trim();
  }

  private static boolean appendNormalizedChar(StringBuilder sb, char c, boolean lastWasSpace) {
    if (Character.isLetterOrDigit(c)) {
      sb.append(c);
      return false;
    }
    return appendSpaceIfNeeded(sb, lastWasSpace);
  }

  private static boolean appendSpaceIfNeeded(StringBuilder sb, boolean lastWasSpace) {
    if (!lastWasSpace && !sb.isEmpty()) {
      sb.append(' ');
      return true;
    }
    return lastWasSpace;
  }

  /** Compare ISO-8601 `ZonedDateTime` strings truncated to the given unit. */
  public static BiPredicate<JsonNode, JsonNode> zonedDateTimeTruncatedTo(ChronoUnit unit) {
    return (l, r) -> compareZonedDateTimes(l, r, unit);
  }

  private static boolean compareZonedDateTimes(JsonNode l, JsonNode r, ChronoUnit unit) {
    if (!bothAreTextual(l, r)) {
      return false;
    }
    try {
      ZonedDateTime a = ZonedDateTime.parse(l.asText());
      ZonedDateTime b = ZonedDateTime.parse(r.asText());
      return a.truncatedTo(unit).equals(b.truncatedTo(unit));
    } catch (Exception e) {
      return false;
    }
  }

  /** Compare `Instant` strings within a tolerance. */
  public static BiPredicate<JsonNode, JsonNode> instantWithin(Duration tolerance) {
    return (l, r) -> compareInstants(l, r, tolerance);
  }

  private static boolean compareInstants(JsonNode l, JsonNode r, Duration tolerance) {
    if (!bothAreTextual(l, r)) {
      return false;
    }
    try {
      Instant a = Instant.parse(l.asText());
      Instant b = Instant.parse(r.asText());
      return isWithinTolerance(a, b, tolerance);
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private static boolean bothAreTextual(JsonNode l, JsonNode r) {
    return l != null && r != null && l.isTextual() && r.isTextual();
  }

  private static boolean isWithinTolerance(Instant a, Instant b, Duration tolerance) {
    long diff = Math.abs(Duration.between(a, b).toMillis());
    return diff <= tolerance.toMillis();
  }
}
