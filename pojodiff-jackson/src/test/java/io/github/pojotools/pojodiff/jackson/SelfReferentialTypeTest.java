package io.github.pojotools.pojodiff.jackson;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.pojodiff.spi.TypeHints;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Data;
import org.junit.jupiter.api.Test;

/** Tests that inferTypeHints handles self-referential types with depth control. */
public class SelfReferentialTypeTest {

  @Test
  void handlesSelfReferentialTypeWithDefaultConfig() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(Schedule.class, mapper);

    // Default config: depth 1
    assertThat(hints.resolve("/name"))
        .as("Root /name should be captured")
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/exclusionSchedules/name"))
        .as("Nested /exclusionSchedules/name should be captured at depth 1")
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/exclusionSchedules/0/name"))
        .as("Array indices should not appear in normalized paths")
        .isEmpty();
  }

  @Test
  void respectsStopAtFirstReference() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(
        Schedule.class, mapper, TypeHintInferenceConfig.stopAtFirstReference());

    // Only root level fields
    assertThat(hints.resolve("/name"))
        .as("Root /name should be captured")
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/exclusionSchedules/name"))
        .as("Nested fields should not be captured with stopAtFirstReference")
        .isEmpty();
  }

  @Test
  void respectsCustomGlobalDepth() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHintInferenceConfig config = TypeHintInferenceConfig.builder()
        .defaultMaxDepth(2)
        .build();
    TypeHints hints = JacksonAdapters.inferTypeHints(Schedule.class, mapper, config);

    // Depth 0: root
    assertThat(hints.resolve("/name"))
        .isPresent()
        .hasValue("java.lang.String");

    // Depth 1
    assertThat(hints.resolve("/exclusionSchedules/name"))
        .isPresent()
        .hasValue("java.lang.String");

    // Depth 2
    assertThat(hints.resolve("/exclusionSchedules/exclusionSchedules/name"))
        .as("Double-nested fields should be captured with maxDepth=2")
        .isPresent()
        .hasValue("java.lang.String");
  }

  @Test
  void supportsPrefixBasedDepthConfiguration() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHintInferenceConfig config = TypeHintInferenceConfig.builder()
        .defaultMaxDepth(1)
        .maxDepthForPrefix("/metadata", 0)  // No recursion under /metadata
        .build();

    TypeHints hints = JacksonAdapters.inferTypeHints(ComplexModel.class, mapper, config);

    // Default depth applies to most paths
    assertThat(hints.resolve("/data"))
        .as("Root /data should be captured")
        .isPresent();

    assertThat(hints.resolve("/temp/value"))
        .as("Nested /temp/value should be captured with default depth")
        .isPresent();

    // Prefix rule limits /metadata paths
    assertThat(hints.resolve("/metadata/tag"))
        .as("Should capture /metadata/tag at depth 0")
        .isPresent();
  }

  @Test
  void supportsPatternBasedDepthConfiguration() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHintInferenceConfig config = TypeHintInferenceConfig.builder()
        .defaultMaxDepth(1)
        .maxDepthForPattern(Pattern.compile("^/temp/.*"), 0)  // No recursion for temp paths
        .build();

    TypeHints hints = JacksonAdapters.inferTypeHints(ComplexModel.class, mapper, config);

    assertThat(hints).isNotNull();
  }

  @Test
  void handlesNestedSelfReferentialType() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHintInferenceConfig config = TypeHintInferenceConfig.builder()
        .defaultMaxDepth(1)
        .build();
    TypeHints hints = JacksonAdapters.inferTypeHints(Node.class, mapper, config);

    assertThat(hints.resolve("/id"))
        .as("Root /id should be captured")
        .isPresent()
        .hasValue("java.lang.Long");

    assertThat(hints.resolve("/children/id"))
        .as("Nested /children/id should be captured")
        .isPresent()
        .hasValue("java.lang.Long");
  }

  @Data
  static class Schedule {
    private String name;
    private List<Schedule> exclusionSchedules;
  }

  @Data
  static class ComplexModel {
    private String data;
    private Metadata metadata;
    private TempData temp;
  }

  @Data
  static class Metadata {
    private String tag;
  }

  @Data
  static class TempData {
    private String value;
  }

  @Data
  static class Node {
    private Long id;
    private List<Node> children;
  }
}
