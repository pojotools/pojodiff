package io.github.pojotools.pojodiff.core;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import io.github.pojotools.pojodiff.core.api.DiffKind;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.engine.DiffEngine;
import io.github.pojotools.pojodiff.core.equivalence.Equivalences;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for Equivalences built-in comparison functions. */
public class EquivalencesTest {

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  /** Helper to create expected CHANGED DiffEntry for assertions. */
  private static DiffEntry changedEntry(String path, JsonNode oldValue, JsonNode newValue) {
    return new DiffEntry(path, DiffKind.CHANGED, oldValue, newValue);
  }

  // Numeric within tests

  @Test
  void numericWithinToleratesSmallDifferences() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode().put("price", 10.005);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void numericWithinDetectsLargeDifferences() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode().put("price", 10.02);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/price", L.get("price"), R.get("price")));
  }

  @Test
  void numericWithinHandlesExactBoundary() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode().put("price", 10.01);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void numericWithinHandlesNegativeDifferences() {
    ObjectNode L = JSON.objectNode().put("price", 10.01);
    ObjectNode R = JSON.objectNode().put("price", 10.00);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void numericWithinHandlesIntegerValues() {
    ObjectNode L = JSON.objectNode().put("count", 100);
    ObjectNode R = JSON.objectNode().put("count", 101);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/count", Equivalences.numericWithin(1.0)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void numericWithinHandlesNullLeft() {
    ObjectNode L = JSON.objectNode();
    L.putNull("price");
    ObjectNode R = JSON.objectNode().put("price", 10.00);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/price", L.get("price"), R.get("price")));
  }

  @Test
  void numericWithinHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode();
    R.putNull("price");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/price", L.get("price"), R.get("price")));
  }

  @Test
  void numericWithinHandlesNonNumericLeft() {
    ObjectNode L = JSON.objectNode().put("price", "not-a-number");
    ObjectNode R = JSON.objectNode().put("price", 10.00);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/price", L.get("price"), R.get("price")));
  }

  @Test
  void numericWithinHandlesNonNumericRight() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode().put("price", "not-a-number");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.01)).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/price", L.get("price"), R.get("price")));
  }

  @Test
  void numericWithinHandlesZeroTolerance() {
    ObjectNode L = JSON.objectNode().put("price", 10.00);
    ObjectNode R = JSON.objectNode().put("price", 10.00);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/price", Equivalences.numericWithin(0.0)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void numericWithinHandlesLargeNumbers() {
    ObjectNode L = JSON.objectNode().put("value", 1000000.00);
    ObjectNode R = JSON.objectNode().put("value", 1000000.50);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/value", Equivalences.numericWithin(1.0)).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  // Case-insensitive tests

  @Test
  void caseInsensitiveConsidersMatchingStringsEquivalent() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "ALICE");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).isEmpty();
  }

  @Test
  void caseInsensitiveDetectsDifferentStrings() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Bob");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", L.get("name"), R.get("name")));
  }

  @Test
  void caseInsensitiveHandlesMixedCase() {
    ObjectNode L = JSON.objectNode().put("text", "HeLLo WoRLd");
    ObjectNode R = JSON.objectNode().put("text", "hello WORLD");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/text", Equivalences.caseInsensitive()).build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  // Punctuation question equals tests

  @Test
  void punctuationQuestionEqualsIgnoresPunctuation() {
    ObjectNode L = JSON.objectNode().put("text", "hello, world?");
    ObjectNode R = JSON.objectNode().put("text", "hello world!");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void punctuationQuestionEqualsDetectsDifferentWords() {
    ObjectNode L = JSON.objectNode().put("text", "hello, world!");
    ObjectNode R = JSON.objectNode().put("text", "goodbye, world!");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/text", L.get("text"), R.get("text")));
  }

  @Test
  void punctuationQuestionEqualsIgnoresMultiplePunctuation() {
    ObjectNode L = JSON.objectNode().put("text", "Hello... World!!!");
    ObjectNode R = JSON.objectNode().put("text", "Hello World");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  // Instant within duration tests

  @Test
  void instantWithinToleratesSmallTimeDifferences() {
    ObjectNode L = JSON.objectNode().put("ts", "2024-01-01T12:00:00Z");
    ObjectNode R = JSON.objectNode().put("ts", "2024-01-01T12:00:00.500Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void instantWithinDetectsLargeTimeDifferences() {
    ObjectNode L = JSON.objectNode().put("ts", "2024-01-01T12:00:00Z");
    ObjectNode R = JSON.objectNode().put("ts", "2024-01-01T12:00:02Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/ts", L.get("ts"), R.get("ts")));
  }

  @Test
  void instantWithinHandlesExactBoundary() {
    ObjectNode L = JSON.objectNode().put("ts", "2024-01-01T12:00:00Z");
    ObjectNode R = JSON.objectNode().put("ts", "2024-01-01T12:00:01Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void instantWithinHandlesMillisecondPrecision() {
    ObjectNode L = JSON.objectNode().put("ts", "2024-01-01T12:00:00.100Z");
    ObjectNode R = JSON.objectNode().put("ts", "2024-01-01T12:00:00.150Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofMillis(100)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  // OffsetDateTime within tests

  @Test
  void offsetDateTimeWithinToleratesSmallTimeDifferences() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T12:00:00.500+00:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void offsetDateTimeWithinDetectsLargeTimeDifferences() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T12:02:00+00:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofMinutes(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/odt", L.get("odt"), R.get("odt")));
  }

  @Test
  void offsetDateTimeWithinHandlesExactBoundary() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T12:00:01+00:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void offsetDateTimeWithinHandlesDifferentTimeZones() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T13:00:00+01:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    // Same instant, different time zones - should be equivalent
    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void offsetDateTimeWithinHandlesMillisecondPrecision() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00.100+00:00");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T12:00:00.150+00:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofMillis(100)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void offsetDateTimeWithinHandlesInvalidDateFormat() {
    ObjectNode L = JSON.objectNode().put("odt", "invalid-date");
    ObjectNode R = JSON.objectNode().put("odt", "also-invalid");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // Should detect difference since parsing fails
    assertThat(diffs).containsExactly(changedEntry("/odt", L.get("odt"), R.get("odt")));
  }

  @Test
  void offsetDateTimeWithinHandlesNullLeft() {
    ObjectNode L = JSON.objectNode();
    L.putNull("odt");
    ObjectNode R = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/odt", L.get("odt"), R.get("odt")));
  }

  @Test
  void offsetDateTimeWithinHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("odt", "2024-01-01T12:00:00+00:00");
    ObjectNode R = JSON.objectNode();
    R.putNull("odt");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/odt", L.get("odt"), R.get("odt")));
  }

  @Test
  void offsetDateTimeWithinHandlesNonTextualValues() {
    ObjectNode L = JSON.objectNode().put("odt", 12345);
    ObjectNode R = JSON.objectNode().put("odt", 67890);

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/odt", Equivalences.offsetDateTimeWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/odt", L.get("odt"), R.get("odt")));
  }

  // ZonedDateTime truncated to tests

  @Test
  void zonedDateTimeTruncatedToIgnoresSubsecondPrecision() {
    ObjectNode L = JSON.objectNode().put("zdt", "2024-01-01T12:00:00.123Z[UTC]");
    ObjectNode R = JSON.objectNode().put("zdt", "2024-01-01T12:00:00.456Z[UTC]");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void zonedDateTimeTruncatedToDetectsDifferentSeconds() {
    ObjectNode L = JSON.objectNode().put("zdt", "2024-01-01T12:00:00.999Z[UTC]");
    ObjectNode R = JSON.objectNode().put("zdt", "2024-01-01T12:00:01.001Z[UTC]");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/zdt", L.get("zdt"), R.get("zdt")));
  }

  @Test
  void zonedDateTimeTruncatedToHandlesMinutePrecision() {
    ObjectNode L = JSON.objectNode().put("zdt", "2024-01-01T12:00:45Z[UTC]");
    ObjectNode R = JSON.objectNode().put("zdt", "2024-01-01T12:00:59Z[UTC]");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.MINUTES))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void zonedDateTimeTruncatedToHandlesDayPrecision() {
    ObjectNode L = JSON.objectNode().put("zdt", "2024-01-01T08:30:45Z[UTC]");
    ObjectNode R = JSON.objectNode().put("zdt", "2024-01-01T18:45:12Z[UTC]");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.DAYS))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  // Combined equivalence tests

  @Test
  void multipleEquivalencesCanBeAppliedToDifferentPaths() {
    ObjectNode L =
        JSON.objectNode()
            .put("name", "Alice")
            .put("text", "hello, world!")
            .put("ts", "2024-01-01T12:00:00Z");
    ObjectNode R =
        JSON.objectNode()
            .put("name", "ALICE")
            .put("text", "hello world")
            .put("ts", "2024-01-01T12:00:00.500Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/name", Equivalences.caseInsensitive())
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    assertThat(DiffEngine.compare(L, R, cfg)).isEmpty();
  }

  @Test
  void equivalencesOnlyApplyToConfiguredPaths() {
    ObjectNode L = JSON.objectNode().put("included", "Hello").put("notIncluded", "Hello");
    ObjectNode R = JSON.objectNode().put("included", "HELLO").put("notIncluded", "HELLO");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/included", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
        .containsExactly(changedEntry("/notIncluded", L.get("notIncluded"), R.get("notIncluded")));
  }

  // Exception path tests

  @Test
  void punctuationQuestionEqualsHandlesNullValues() {
    ObjectNode L = JSON.objectNode();
    L.putNull("text");
    ObjectNode R = JSON.objectNode().put("text", "hello");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/text", L.get("text"), R.get("text")));
  }

  @Test
  void punctuationQuestionEqualsHandlesNonTextualValues() {
    ObjectNode L = JSON.objectNode().put("text", 123);
    ObjectNode R = JSON.objectNode().put("text", 456);

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/text", L.get("text"), R.get("text")));
  }

  @Test
  void zonedDateTimeTruncatedToHandlesInvalidDateFormat() {
    ObjectNode L = JSON.objectNode().put("zdt", "invalid-date");
    ObjectNode R = JSON.objectNode().put("zdt", "also-invalid");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // Should detect difference since parsing fails
    assertThat(diffs).containsExactly(changedEntry("/zdt", L.get("zdt"), R.get("zdt")));
  }

  @Test
  void zonedDateTimeTruncatedToHandlesNonTextualValues() {
    ObjectNode L = JSON.objectNode().put("zdt", 123456);
    ObjectNode R = JSON.objectNode().put("zdt", 789012);

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/zdt", L.get("zdt"), R.get("zdt")));
  }

  @Test
  void instantWithinHandlesInvalidDateFormat() {
    ObjectNode L = JSON.objectNode().put("ts", "not-a-date");
    ObjectNode R = JSON.objectNode().put("ts", "also-not-a-date");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // Should detect difference since parsing fails
    assertThat(diffs).containsExactly(changedEntry("/ts", L.get("ts"), R.get("ts")));
  }

  @Test
  void instantWithinHandlesNonTextualValues() {
    ObjectNode L = JSON.objectNode().put("ts", 12345);
    ObjectNode R = JSON.objectNode().put("ts", 67890);

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/ts", L.get("ts"), R.get("ts")));
  }

  // Branch coverage tests

  @Test
  void caseInsensitiveHandlesNullLeft() {
    ObjectNode L = JSON.objectNode();
    L.putNull("name");
    ObjectNode R = JSON.objectNode().put("name", "Alice");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", L.get("name"), R.get("name")));
  }

  @Test
  void caseInsensitiveHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode();
    R.putNull("name");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", L.get("name"), R.get("name")));
  }

  @Test
  void caseInsensitiveHandlesBothNull() {
    ObjectNode L = JSON.objectNode();
    L.putNull("name");
    ObjectNode R = JSON.objectNode();
    R.putNull("name");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // Both null are equal (no diff)
    assertThat(diffs).isEmpty();
  }

  @Test
  void zonedDateTimeTruncatedToHandlesNullLeft() {
    ObjectNode L = JSON.objectNode();
    L.putNull("zdt");
    ObjectNode R = JSON.objectNode().put("zdt", "2024-01-01T12:00:00Z[UTC]");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/zdt", L.get("zdt"), R.get("zdt")));
  }

  @Test
  void zonedDateTimeTruncatedToHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("zdt", "2024-01-01T12:00:00Z[UTC]");
    ObjectNode R = JSON.objectNode();
    R.putNull("zdt");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/zdt", Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.SECONDS))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/zdt", L.get("zdt"), R.get("zdt")));
  }

  @Test
  void instantWithinHandlesNullLeft() {
    ObjectNode L = JSON.objectNode();
    L.putNull("ts");
    ObjectNode R = JSON.objectNode().put("ts", "2024-01-01T12:00:00Z");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/ts", L.get("ts"), R.get("ts")));
  }

  @Test
  void instantWithinHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("ts", "2024-01-01T12:00:00Z");
    ObjectNode R = JSON.objectNode();
    R.putNull("ts");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/ts", Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/ts", L.get("ts"), R.get("ts")));
  }

  @Test
  void punctuationQuestionEqualsHandlesEmptyString() {
    ObjectNode L = JSON.objectNode().put("text", "");
    ObjectNode R = JSON.objectNode().put("text", "");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).isEmpty();
  }

  @Test
  void punctuationQuestionEqualsHandlesOnlyPunctuation() {
    ObjectNode L = JSON.objectNode().put("text", "!!!");
    ObjectNode R = JSON.objectNode().put("text", "???");

    DiffConfig cfg =
        DiffConfig.builder()
            .equivalentAt("/text", Equivalences.punctuationQuestionEquals())
            .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // Only punctuation normalizes to empty string
    assertThat(diffs).isEmpty();
  }

  @Test
  void caseInsensitiveHandlesNonTextualLeft() {
    ObjectNode L = JSON.objectNode().put("name", 123);
    ObjectNode R = JSON.objectNode().put("name", "alice");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", L.get("name"), R.get("name")));
  }

  @Test
  void caseInsensitiveHandlesNonTextualRight() {
    ObjectNode L = JSON.objectNode().put("name", "alice");
    ObjectNode R = JSON.objectNode().put("name", 123);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", L.get("name"), R.get("name")));
  }
}
