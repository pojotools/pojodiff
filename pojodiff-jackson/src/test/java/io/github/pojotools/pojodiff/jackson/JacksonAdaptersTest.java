package io.github.pojotools.pojodiff.jackson;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.pojodiff.spi.TypeHints;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import lombok.Data;

/** Tests for JacksonAdapters type hint inference. */
public class JacksonAdaptersTest {

  @Test
  void infersBasicFieldTypes() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(BasicTypes.class, mapper);

    assertThat(hints.resolve("/name"))
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/age"))
        .isPresent()
        .hasValue("int");

    assertThat(hints.resolve("/active"))
        .isPresent()
        .hasValue("boolean");
  }

  @Test
  void unwrapsOptionalTypes() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(OptionalTypes.class, mapper);

    // Optional<String> should resolve to String
    assertThat(hints.resolve("/optionalName"))
        .isPresent()
        .hasValue("java.lang.String");

    // Optional<Instant> should resolve to Instant
    assertThat(hints.resolve("/optionalTimestamp"))
        .isPresent()
        .hasValue("java.time.Instant");

    // Nested Optional in complex object
    assertThat(hints.resolve("/optionalUser/email"))
        .isPresent()
        .hasValue("java.lang.String");
  }

  @Test
  void unwrapsOptionalIntLongDouble() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(SpecialOptionalTypes.class, mapper);

    // OptionalInt should resolve to Integer
    assertThat(hints.resolve("/optionalCount"))
        .isPresent()
        .hasValue("java.lang.Integer");

    // OptionalLong should resolve to Long
    assertThat(hints.resolve("/optionalId"))
        .isPresent()
        .hasValue("java.lang.Long");

    // OptionalDouble should resolve to Double
    assertThat(hints.resolve("/optionalRate"))
        .isPresent()
        .hasValue("java.lang.Double");
  }

  @Test
  void extractsCollectionElementTypes() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(CollectionTypes.class, mapper);

    // List<String> should extract String element type
    assertThat(hints.resolve("/tags"))
        .isPresent()
        .hasValue("java.lang.String");

    // List<User> should extract User fields
    assertThat(hints.resolve("/users/name"))
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/users/email"))
        .isPresent()
        .hasValue("java.lang.String");

    // Set<Integer> should extract Integer element type
    assertThat(hints.resolve("/numbers"))
        .isPresent()
        .hasValue("java.lang.Integer");
  }

  @Test
  void extractsArrayElementTypes() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(ArrayTypes.class, mapper);

    // String[] should extract String element type
    assertThat(hints.resolve("/tags"))
        .isPresent()
        .hasValue("java.lang.String");

    // User[] should extract User fields
    assertThat(hints.resolve("/users/name"))
        .isPresent()
        .hasValue("java.lang.String");

    // int[] should extract int element type
    assertThat(hints.resolve("/scores"))
        .isPresent()
        .hasValue("int");
  }

  @Test
  void handlesNestedCollections() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(NestedCollections.class, mapper);

    // List<List<String>> should extract String from nested lists
    assertThat(hints.resolve("/matrix"))
        .isPresent()
        .hasValue("java.lang.String");

    // List<Set<Integer>> should extract Integer
    assertThat(hints.resolve("/groups"))
        .isPresent()
        .hasValue("java.lang.Integer");
  }

  @Test
  void handlesOptionalCollections() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(OptionalCollections.class, mapper);

    // Optional<List<String>> should unwrap to List<String> then extract String
    assertThat(hints.resolve("/optionalTags"))
        .isPresent()
        .hasValue("java.lang.String");

    // Optional<List<User>> should extract User fields
    assertThat(hints.resolve("/optionalUsers/name"))
        .isPresent()
        .hasValue("java.lang.String");
  }

  @Test
  void handlesCollectionOfOptionals() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(CollectionOfOptionals.class, mapper);

    // List<Optional<String>> should extract String (unwrapping Optional)
    assertThat(hints.resolve("/optionalNames"))
        .isPresent()
        .hasValue("java.lang.String");

    // List<Optional<User>> should extract User fields
    assertThat(hints.resolve("/optionalUsers/email"))
        .isPresent()
        .hasValue("java.lang.String");
  }

  @Test
  void handlesComplexNestedStructures() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(ComplexNested.class, mapper);

    assertThat(hints.resolve("/id"))
        .isPresent()
        .hasValue("java.lang.Long");

    assertThat(hints.resolve("/metadata/tags"))
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/items/name"))
        .isPresent()
        .hasValue("java.lang.String");

    assertThat(hints.resolve("/items/quantity"))
        .isPresent()
        .hasValue("int");

    assertThat(hints.resolve("/items/price"))
        .isPresent()
        .hasValue("java.math.BigDecimal");
  }

  @Test
  void doesNotCreateHintsForCollectionFieldsThemselves() {
    ObjectMapper mapper = new ObjectMapper();
    TypeHints hints = JacksonAdapters.inferTypeHints(CollectionTypes.class, mapper);

    // Collection fields themselves should not have type hints
    // Only their element types should be captured
    assertThat(hints.resolve("/tags")).isPresent(); // Element type
    assertThat(hints.resolve("/users")).isEmpty(); // Collection field itself
  }

  // Test model classes

  @Data
  static class BasicTypes {
    private String name;
    private int age;
    private boolean active;
  }

  @Data
  static class User {
    private String name;
    private String email;
  }

  @Data
  static class OptionalTypes {
    private Optional<String> optionalName;
    private Optional<Instant> optionalTimestamp;
    private Optional<User> optionalUser;
  }

  @Data
  static class SpecialOptionalTypes {
    private OptionalInt optionalCount;
    private OptionalLong optionalId;
    private OptionalDouble optionalRate;
  }

  @Data
  static class CollectionTypes {
    private List<String> tags;
    private List<User> users;
    private Set<Integer> numbers;
  }

  @Data
  static class ArrayTypes {
    private String[] tags;
    private User[] users;
    private int[] scores;
  }

  @Data
  static class NestedCollections {
    private List<List<String>> matrix;
    private List<Set<Integer>> groups;
  }

  @Data
  static class OptionalCollections {
    private Optional<List<String>> optionalTags;
    private Optional<List<User>> optionalUsers;
  }

  @Data
  static class CollectionOfOptionals {
    private List<Optional<String>> optionalNames;
    private List<Optional<User>> optionalUsers;
  }

  @Data
  static class Item {
    private String name;
    private int quantity;
    private BigDecimal price;
  }

  @Data
  static class Metadata {
    private List<String> tags;
  }

  @Data
  static class ComplexNested {
    private Long id;
    private Metadata metadata;
    private List<Item> items;
  }
}
