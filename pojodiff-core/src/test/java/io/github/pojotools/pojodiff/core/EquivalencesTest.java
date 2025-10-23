package io.github.pojotools.pojodiff.core;

import static org.assertj.core.api.Assertions.*;

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

    assertThat(diffs)
        .hasSize(1)
        .first()
        .satisfies(
            diff -> {
              assertThat(diff.path()).isEqualTo("/name");
              assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
              assertThat(diff.oldValue().asText()).isEqualTo("Alice");
              assertThat(diff.newValue().asText()).isEqualTo("Bob");
            });
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

    assertThat(diffs)
        .hasSize(1)
        .first()
        .satisfies(
            diff -> {
              assertThat(diff.path()).isEqualTo("/text");
              assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
            });
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

    assertThat(diffs)
        .hasSize(1)
        .first()
        .satisfies(
            diff -> {
              assertThat(diff.path()).isEqualTo("/ts");
              assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
              assertThat(diff.oldValue().asText()).isEqualTo("2024-01-01T12:00:00Z");
              assertThat(diff.newValue().asText()).isEqualTo("2024-01-01T12:00:02Z");
            });
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

    assertThat(diffs)
        .hasSize(1)
        .first()
        .satisfies(
            diff -> {
              assertThat(diff.path()).isEqualTo("/zdt");
              assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
            });
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
        .hasSize(1)
        .first()
        .satisfies(
            diff -> {
              assertThat(diff.path()).isEqualTo("/notIncluded");
              assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
            });
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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
    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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
    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
  }

  @Test
  void caseInsensitiveHandlesNullRight() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode();
    R.putNull("name");

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
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

    assertThat(diffs).hasSize(1);
  }

  @Test
  void caseInsensitiveHandlesNonTextualRight() {
    ObjectNode L = JSON.objectNode().put("name", "alice");
    ObjectNode R = JSON.objectNode().put("name", 123);

    DiffConfig cfg =
        DiffConfig.builder().equivalentAt("/name", Equivalences.caseInsensitive()).build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).hasSize(1);
  }
}
