package io.github.pojotools.pojodiff.core;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import io.github.pojotools.pojodiff.core.api.DiffKind;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.engine.DiffEngine;
import io.github.pojotools.pojodiff.core.equivalence.Equivalences;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Tests for DiffEngine comparison functionality.
 */
public class DiffEngineTest {

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ObjectNode parseJson(String json) {
    try {
      return (ObjectNode) MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to parse JSON", e);
    }
  }

  // Helper methods to create expected DiffEntry objects

  private static DiffEntry changedEntry(String path, Object oldValue, Object newValue) {
    return new DiffEntry(path, DiffKind.CHANGED, toJsonNode(oldValue), toJsonNode(newValue));
  }

  private static DiffEntry addedEntry(String path, Object newValue) {
    // For array elements with ID-based matching, use ADDED; for simple fields, use CHANGED
    DiffKind kind =
      path.endsWith("}") || path.matches(".*\\d+$") ? DiffKind.ADDED : DiffKind.CHANGED;
    return new DiffEntry(path, kind, null, toJsonNode(newValue));
  }

  private static DiffEntry removedEntry(String path, Object oldValue) {
    // For array elements with ID-based matching, use REMOVED; for simple fields, use CHANGED
    DiffKind kind =
      path.endsWith("}") || path.matches(".*\\d+$") ? DiffKind.REMOVED : DiffKind.CHANGED;
    return new DiffEntry(path, kind, toJsonNode(oldValue), null);
  }

  private static JsonNode toJsonNode(Object value) {
    return switch (value) {
      case null -> null;
      case JsonNode jsonNode -> jsonNode;
      case String s -> JSON.textNode(s);
      case Integer i -> JSON.numberNode(i);
      case Long l -> JSON.numberNode(l);
      case Double v -> JSON.numberNode(v);
      case Boolean b -> JSON.booleanNode(b);
      default -> throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
    };
  }

  // Basic value comparison tests

  @Test
  void detectsSimpleValueChange() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Bob");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  @Test
  void detectsMultipleValueChanges() {
    ObjectNode L = JSON.objectNode().put("name", "Alice").put("age", 30);
    ObjectNode R = JSON.objectNode().put("name", "Bob").put("age", 25);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/name", "Alice", "Bob"), changedEntry("/age", 30, 25));
  }

  @Test
  void detectsAddedField() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Alice").put("email", "alice@example.com");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(addedEntry("/email", "alice@example.com"));
  }

  @Test
  void detectsRemovedField() {
    ObjectNode L = JSON.objectNode().put("name", "Alice").put("age", 30);
    ObjectNode R = JSON.objectNode().put("name", "Alice");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(removedEntry("/age", 30));
  }

  @Test
  void detectsNestedObjectChanges() {
    ObjectNode L =
      parseJson(
        """
          {
            "address": {
              "city": "NYC",
              "zip": "10001"
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "address": {
              "city": "LA",
              "zip": "90001"
            }
          }
          """);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/address/city", "NYC", "LA"),
        changedEntry("/address/zip", "10001", "90001"));
  }

  @Test
  void considersIdenticalObjectsEqual() {
    ObjectNode L = JSON.objectNode().put("name", "Alice").put("age", 30);
    ObjectNode R = JSON.objectNode().put("name", "Alice").put("age", 30);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).isEmpty();
  }

  // Array comparison tests - by index

  @Test
  void detectsArrayElementChangeByIndex() {
    ObjectNode L =
      parseJson(
        """
          {
            "tags": ["a", "b", "c"]
          }
          """);
    ObjectNode R =
      parseJson(
        """
          {
            "tags": ["a", "x", "c"]
          }
          """);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(changedEntry("/tags/1", "b", "x"));
  }

  @Test
  void detectsAddedArrayElement() {
    ObjectNode L =
      parseJson(
        """
          {
            "tags": ["a", "b"]
          }
          """);
    ObjectNode R =
      parseJson(
        """
          {
            "tags": ["a", "b", "c"]
          }
          """);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(addedEntry("/tags/2", "c"));
  }

  @Test
  void detectsRemovedArrayElement() {
    ObjectNode L =
      parseJson(
        """
          {
            "tags": ["a", "b", "c"]
          }
          """);
    ObjectNode R =
      parseJson(
        """
          {
            "tags": ["a", "b"]
          }
          """);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(removedEntry("/tags/2", "c"));
  }

  // Array comparison tests - by ID

  @Test
  void matchesArrayElementsByFieldIdentifier() {
    ObjectNode L =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "name": "Item A", "value": 100},
              {"id": "2", "name": "Item B", "value": 200}
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "name": "Item A Modified", "value": 100},
              {"id": "2", "name": "Item B", "value": 250}
            ]
          }
          """);

    DiffConfig cfg = DiffConfig.builder().list("/items", ListRule.id("id")).build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/items/{1}/name", "Item A", "Item A Modified"),
        changedEntry("/items/{2}/value", 200, 250));
  }

  @Test
  void matchesArrayElementsByJsonPointerIdentifier() {
    ObjectNode L =
      parseJson(
        """
          {
            "tasks": [
              {
                "meta": {"taskId": "task-1"},
                "status": "pending"
              },
              {
                "meta": {"taskId": "task-2"},
                "status": "done"
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "tasks": [
              {
                "meta": {"taskId": "task-1"},
                "status": "done"
              },
              {
                "meta": {"taskId": "task-2"},
                "status": "done"
              }
            ]
          }
          """);

    DiffConfig cfg = DiffConfig.builder().list("/tasks", ListRule.id("/meta/taskId")).build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/tasks/{task-1}/status", "pending", "done"));
  }

  @Test
  void detectsAddedArrayElementById() {
    ObjectNode L =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "value": 100}
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "value": 100},
              {"id": "2", "value": 200}
            ]
          }
          """);

    DiffConfig cfg = DiffConfig.builder().list("/items", ListRule.id("id")).build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        addedEntry(
          "/items/{2}",
          parseJson(
            """
              {"id": "2", "value": 200}
              """)));
  }

  @Test
  void detectsRemovedArrayElementById() {
    ObjectNode L =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "value": 100},
              {"id": "2", "value": 200}
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "items": [
              {"id": "1", "value": 100}
            ]
          }
          """);

    DiffConfig cfg = DiffConfig.builder().list("/items", ListRule.id("id")).build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        removedEntry(
          "/items/{2}",
          parseJson(
            """
              {"id": "2", "value": 200}
              """)));
  }

  // Ignore rules tests

  @Test
  void respectsExactIgnoreRule() {
    ObjectNode L = JSON.objectNode().put("name", "Alice").put("temp", "old");
    ObjectNode R = JSON.objectNode().put("name", "Bob").put("temp", "new");

    DiffConfig cfg = DiffConfig.builder().ignore("/temp").build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  @Test
  void respectsIgnorePrefixRule() {
    ObjectNode L =
      parseJson(
        """
          {
            "name": "Alice",
            "meta": {
              "created": "2024-01-01",
              "updated": "2024-01-02"
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "name": "Bob",
            "meta": {
              "created": "2024-02-01",
              "updated": "2024-02-02"
            }
          }
          """);

    DiffConfig cfg = DiffConfig.builder().ignorePrefix("/meta").build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  @Test
  void respectsIgnorePatternRule() {
    ObjectNode L =
      parseJson(
        """
          {
            "name": "Alice",
            "temp1": "old",
            "temp2": "old",
            "data": "important"
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "name": "Bob",
            "temp1": "new",
            "temp2": "new",
            "data": "important"
          }
          """);

    DiffConfig cfg = DiffConfig.builder().ignorePattern(Pattern.compile("^/temp.*$")).build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  @Test
  void respectsIgnoreGlobRule() {
    ObjectNode L =
      parseJson(
        """
          {
            "name": "Alice",
            "config": {
              "metadata": {
                "version": "1.0"
              }
            },
            "data": {
              "metadata": {
                "source": "api"
              }
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "name": "Bob",
            "config": {
              "metadata": {
                "version": "2.0"
              }
            },
            "data": {
              "metadata": {
                "source": "file"
              }
            }
          }
          """);

    DiffConfig cfg = DiffConfig.builder().ignoreGlob("/**/metadata/**").build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  @Test
  void combinesMultipleIgnoreRules() {
    ObjectNode L =
      parseJson(
        """
          {
            "name": "Alice",
            "exact": "old",
            "temp": "old",
            "meta": {
              "x": "old"
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "name": "Bob",
            "exact": "new",
            "temp": "new",
            "meta": {
              "x": "new"
            }
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .ignore("/exact")
        .ignorePrefix("/meta")
        .ignorePattern(Pattern.compile("^/temp$"))
        .build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  // Equivalence tests

  @Test
  void appliesExactPathEquivalence() {
    ObjectNode L = JSON.objectNode().put("name", "Alice").put("other", "value1");
    ObjectNode R = JSON.objectNode().put("name", "ALICE").put("other", "value2");

    DiffConfig cfg =
      DiffConfig.builder()
        .equivalentAt("/name", (l, r) -> l.asText().equalsIgnoreCase(r.asText()))
        .build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/other", "value1", "value2"));
  }

  @Test
  void exactEquivalenceTakesPrecedenceOverOtherRules() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Bob");

    DiffConfig cfg =
      DiffConfig.builder()
        .equivalentUnder("/name", (l, r) -> false)
        .equivalentPattern(Pattern.compile("^/name$"), (l, r) -> false)
        .equivalentFallback((l, r) -> false)
        .equivalentAt("/name", (l, r) -> true) // Exact takes precedence
        .build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).isEmpty();
  }

  @Test
  void appliesTypeScopedEquivalenceWithTypeHint() {
    ObjectNode L = JSON.objectNode().put("timestamp", "2024-01-01T12:00:00Z");
    ObjectNode R = JSON.objectNode().put("timestamp", "2024-01-01T12:00:00.500Z");

    DiffConfig cfg =
      DiffConfig.builder()
        .typeHint("/timestamp", "java.time.Instant")
        .equivalentForType(
          "java.time.Instant", Equivalences.instantWithin(Duration.ofSeconds(1)))
        .build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).isEmpty();
  }

  // Root path tests

  @Test
  void appliesCustomRootPath() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Bob");

    DiffConfig cfg = DiffConfig.builder().rootPath("/root").build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs).containsExactly(changedEntry("/root/name", "Alice", "Bob"));
  }

  @Test
  void usesDefaultRootPathWhenNotSpecified() {
    ObjectNode L = JSON.objectNode().put("name", "Alice");
    ObjectNode R = JSON.objectNode().put("name", "Bob");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).containsExactly(changedEntry("/name", "Alice", "Bob"));
  }

  // Complex scenario tests

  @Test
  void handlesComplexNestedStructureWithMultipleRules() {
    ObjectNode L =
      parseJson(
        """
          {
            "name": "Alice",
            "meta": {
              "created": "2024-01-01"
            },
            "items": [
              {"id": "1", "value": 100, "status": "active"},
              {"id": "2", "value": 200, "status": "inactive"}
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "name": "ALICE",
            "meta": {
              "created": "2024-02-01"
            },
            "items": [
              {"id": "1", "value": 150, "status": "active"},
              {"id": "2", "value": 200, "status": "active"}
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .equivalentAt("/name", Equivalences.caseInsensitive())
        .ignorePrefix("/meta")
        .list("/items", ListRule.id("id"))
        .build();
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/items/{1}/value", 100, 150),
        changedEntry("/items/{2}/status", "inactive", "active"));
  }

  @Test
  void handlesEmptyObjects() {
    ObjectNode L = JSON.objectNode();
    ObjectNode R = JSON.objectNode();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).isEmpty();
  }

  @Test
  void handlesEmptyArrays() {
    ObjectNode L =
      parseJson(
        """
          {
            "items": []
          }
          """);
    ObjectNode R =
      parseJson(
        """
          {
            "items": []
          }
          """);

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).isEmpty();
  }

  @Test
  void detectsTypeChanges() {
    ObjectNode L = JSON.objectNode().put("value", 123);
    ObjectNode R = JSON.objectNode().put("value", "123");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).hasSize(1);
    DiffEntry diff = diffs.getFirst();
    assertThat(diff.path()).isEqualTo("/value");
    assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
    assertThat(diff.oldValue().isNumber()).isTrue();
    assertThat(diff.newValue().isTextual()).isTrue();
  }

  @Test
  void detectsTypeChangeFromValueToObject() {
    // Test case where left is a value and right is an object (or vice versa)
    ObjectNode L = JSON.objectNode().put("field", "simpleValue");
    ObjectNode R = JSON.objectNode();
    R.set("field", JSON.objectNode().put("nested", "value"));

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).hasSize(1);
    DiffEntry diff = diffs.getFirst();
    assertThat(diff.path()).isEqualTo("/field");
    assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
    assertThat(diff.oldValue().isTextual()).isTrue();
    assertThat(diff.newValue().isObject()).isTrue();
  }

  @Test
  void detectsTypeChangeFromObjectToValue() {
    ObjectNode L = JSON.objectNode();
    L.set("field", JSON.objectNode().put("nested", "value"));
    ObjectNode R = JSON.objectNode().put("field", "simpleValue");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).hasSize(1);
    DiffEntry diff = diffs.getFirst();
    assertThat(diff.path()).isEqualTo("/field");
    assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
    assertThat(diff.oldValue().isObject()).isTrue();
    assertThat(diff.newValue().isTextual()).isTrue();
  }

  @Test
  void detectsTypeChangeFromArrayToValue() {
    ObjectNode L =
      parseJson(
        """
          {
            "field": [1, 2, 3]
          }
          """);
    ObjectNode R = JSON.objectNode().put("field", "notAnArray");

    List<DiffEntry> diffs = DiffEngine.compare(L, R, DiffConfig.builder().build());

    assertThat(diffs).hasSize(1);
    DiffEntry diff = diffs.getFirst();
    assertThat(diff.path()).isEqualTo("/field");
    assertThat(diff.kind()).isEqualTo(DiffKind.CHANGED);
    assertThat(diff.oldValue().isArray()).isTrue();
    assertThat(diff.newValue().isTextual()).isTrue();
  }

  // Nested list identity matching tests

  @Test
  void matchesNestedArrayElementsByNormalizedPath() {
    ObjectNode L =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "name": "Engineering",
                "members": [
                  {"empId": "E001", "name": "Alice", "role": "Engineer"},
                  {"empId": "E002", "name": "Bob", "role": "Manager"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "name": "Engineering",
                "members": [
                  {"empId": "E001", "name": "Alice", "role": "Senior Engineer"},
                  {"empId": "E002", "name": "Bob", "role": "Manager"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/teams", ListRule.id("id"))
        .list("/teams/members", ListRule.id("empId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        changedEntry("/teams/{team-1}/members/{E001}/role", "Engineer", "Senior Engineer"));
  }

  @Test
  void matchesNestedArraysAcrossMultipleParentInstances() {
    // Test that normalized path /teams/members applies to all team instances
    ObjectNode L =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"}
                ]
              },
              {
                "id": "team-2",
                "members": [
                  {"empId": "E101", "name": "Charlie"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice Smith"}
                ]
              },
              {
                "id": "team-2",
                "members": [
                  {"empId": "E101", "name": "Charlie Brown"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/teams", ListRule.id("id"))
        .list("/teams/members", ListRule.id("empId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/teams/{team-1}/members/{E001}/name", "Alice", "Alice Smith"),
        changedEntry("/teams/{team-2}/members/{E101}/name", "Charlie", "Charlie Brown"));
  }

  @Test
  void detectsNestedArrayElementAdditionWithNormalizedPath() {
    ObjectNode L =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"},
                  {"empId": "E002", "name": "Bob"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/teams", ListRule.id("id"))
        .list("/teams/members", ListRule.id("empId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        addedEntry(
          "/teams/{team-1}/members/{E002}",
          parseJson(
            """
              {"empId": "E002", "name": "Bob"}
              """)));
  }

  @Test
  void detectsNestedArrayElementRemovalWithNormalizedPath() {
    ObjectNode L =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"},
                  {"empId": "E002", "name": "Bob"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/teams", ListRule.id("id"))
        .list("/teams/members", ListRule.id("empId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        removedEntry(
          "/teams/{team-1}/members/{E002}",
          parseJson(
            """
              {"empId": "E002", "name": "Bob"}
              """)));
  }

  @Test
  void handlesNestedArraysWithoutNormalizedPathFallbacksToIndex() {
    // Without normalized path config, nested arrays should pair by index
    ObjectNode L =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E001", "name": "Alice"},
                  {"empId": "E002", "name": "Bob"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "teams": [
              {
                "id": "team-1",
                "members": [
                  {"empId": "E002", "name": "Bob"},
                  {"empId": "E001", "name": "Alice"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/teams", ListRule.id("id"))
        // No normalized path for /teams/members - falls back to index pairing
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // With index pairing, reordering causes changes at both positions for all fields
    assertThat(diffs).containsExactlyInAnyOrder(
      changedEntry("/teams/{team-1}/members/0/empId", "E001", "E002"),
      changedEntry("/teams/{team-1}/members/0/name", "Alice", "Bob"),
      changedEntry("/teams/{team-1}/members/1/empId", "E002", "E001"),
      changedEntry("/teams/{team-1}/members/1/name", "Bob", "Alice")
    );
  }

  @Test
  void handlesThreeLevelNestedArrays() {
    // Structure: departments -> teams -> members
    ObjectNode L =
      parseJson(
        """
          {
            "departments": [
              {
                "id": "dept-1",
                "teams": [
                  {
                    "id": "team-1",
                    "members": [
                      {"empId": "E001", "name": "Alice", "salary": 100000}
                    ]
                  }
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "departments": [
              {
                "id": "dept-1",
                "teams": [
                  {
                    "id": "team-1",
                    "members": [
                      {"empId": "E001", "name": "Alice", "salary": 110000}
                    ]
                  }
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/departments", ListRule.id("id"))
        .list("/departments/teams", ListRule.id("id"))
        .list("/departments/teams/members", ListRule.id("empId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        changedEntry(
          "/departments/{dept-1}/teams/{team-1}/members/{E001}/salary", 100000, 110000));
  }

  @Test
  void normalizedPathWorksWithJsonPointerIdentity() {
    // Nested arrays where parent uses JSON Pointer for identity
    ObjectNode L =
      parseJson(
        """
          {
            "projects": [
              {
                "meta": {"projectId": "proj-1"},
                "tasks": [
                  {"taskId": "task-1", "status": "pending"}
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "projects": [
              {
                "meta": {"projectId": "proj-1"},
                "tasks": [
                  {"taskId": "task-1", "status": "done"}
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/projects", ListRule.id("/meta/projectId"))
        .list("/projects/tasks", ListRule.id("taskId"))
        .build();

    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    assertThat(diffs)
      .containsExactly(
        changedEntry("/projects/{proj-1}/tasks/{task-1}/status", "pending", "done"));
  }

  // Self-referential object tests

  @Test
  void handlesSelfReferentialObjectWithNoChanges() {
    // ARRANGE - Two identical self-referential structures
    ObjectNode L =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder().list("/exclusionSchedules", ListRule.id("id")).build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs).isEmpty();
  }

  @Test
  void detectsChangesInSelfReferentialObject() {
    // ARRANGE - Self-referential structure with a change in nested schedule
    ObjectNode L =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1 Modified",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder().list("/exclusionSchedules", ListRule.id("id")).build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs)
      .containsExactly(
        changedEntry("/exclusionSchedules/{schedule-2}/name", "Exclusion 1", "Exclusion 1 Modified"));
  }

  @Test
  void detectsAddedElementInSelfReferentialObject() {
    // ARRANGE - Adding a new exclusion schedule to a self-referential structure
    ObjectNode L =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": []
              },
              {
                "id": "schedule-3",
                "name": "Exclusion 2",
                "exclusionSchedules": []
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder().list("/exclusionSchedules", ListRule.id("id")).build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs)
      .containsExactly(
        addedEntry(
          "/exclusionSchedules/{schedule-3}",
          parseJson(
            """
              {
                "id": "schedule-3",
                "name": "Exclusion 2",
                "exclusionSchedules": []
              }
              """)));
  }

  @Test
  void handlesDeepSelfReferentialStructure() {
    // ARRANGE - Multiple levels of self-referential objects
    // Note: Nested exclusionSchedules use index-based matching unless configured separately
    ObjectNode L =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": [
                  {
                    "id": "schedule-3",
                    "name": "Nested Exclusion",
                    "exclusionSchedules": []
                  }
                ]
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "exclusionSchedules": [
                  {
                    "id": "schedule-3",
                    "name": "Nested Exclusion Updated",
                    "exclusionSchedules": []
                  }
                ]
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/exclusionSchedules", ListRule.id("id"))
        .build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT - Nested exclusionSchedules use index-based matching (0, 1, etc.)
    assertThat(diffs)
      .containsExactly(
        changedEntry(
          "/exclusionSchedules/{schedule-2}/exclusionSchedules/0/name",
          "Nested Exclusion",
          "Nested Exclusion Updated"));
  }

  @Test
  void handlesSelfReferentialObjectEmbeddedInParent() {
    // ARRANGE - Self-referential Schedule embedded in a parent Meeting object
    ObjectNode L =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync",
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion",
                  "exclusionSchedules": []
                }
              ]
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync",
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion",
                  "exclusionSchedules": []
                }
              ]
            }
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/schedule/exclusionSchedules", ListRule.id("id"))
        .build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs).isEmpty();
  }

  @Test
  void detectsChangesInSelfReferentialObjectEmbeddedInParent() {
    // ARRANGE - Change in self-referential Schedule embedded in parent Meeting
    ObjectNode L =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync",
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion",
                  "exclusionSchedules": []
                }
              ]
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync",
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule Modified",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion Updated",
                  "exclusionSchedules": []
                }
              ]
            }
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/schedule/exclusionSchedules", ListRule.id("id"))
        .build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/schedule/name", "Weekly Schedule", "Weekly Schedule Modified"),
        changedEntry(
          "/schedule/exclusionSchedules/{schedule-2}/name",
          "Holiday Exclusion",
          "Holiday Exclusion Updated"));
  }

  @Test
  void handlesComplexParentWithDeeplySelfReferentialObject() {
    // ARRANGE - Complex parent with multiple levels of self-referential nesting
    // Note: Nested exclusionSchedules use index-based matching unless configured separately
    ObjectNode L =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync",
            "attendees": ["alice@example.com", "bob@example.com"],
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule",
              "startTime": "09:00",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion",
                  "startTime": "00:00",
                  "exclusionSchedules": [
                    {
                      "id": "schedule-3",
                      "name": "Nested Holiday",
                      "startTime": "00:00",
                      "exclusionSchedules": []
                    }
                  ]
                }
              ]
            }
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "meetingId": "meeting-1",
            "title": "Team Sync Updated",
            "attendees": ["alice@example.com", "bob@example.com"],
            "schedule": {
              "id": "schedule-1",
              "name": "Weekly Schedule",
              "startTime": "10:00",
              "exclusionSchedules": [
                {
                  "id": "schedule-2",
                  "name": "Holiday Exclusion",
                  "startTime": "00:00",
                  "exclusionSchedules": [
                    {
                      "id": "schedule-3",
                      "name": "Nested Holiday Modified",
                      "startTime": "00:00",
                      "exclusionSchedules": []
                    }
                  ]
                }
              ]
            }
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder()
        .list("/schedule/exclusionSchedules", ListRule.id("id"))
        .build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT - Nested exclusionSchedules use index-based matching (0, 1, etc.)
    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/title", "Team Sync", "Team Sync Updated"),
        changedEntry("/schedule/startTime", "09:00", "10:00"),
        changedEntry(
          "/schedule/exclusionSchedules/{schedule-2}/exclusionSchedules/0/name",
          "Nested Holiday",
          "Nested Holiday Modified"));
  }

  @Test
  void detectsMultipleChangesInSelfReferentialObjectArray() {
    // ARRANGE - Multiple changes across different self-referential objects
    ObjectNode L =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1",
                "active": true,
                "exclusionSchedules": []
              },
              {
                "id": "schedule-3",
                "name": "Exclusion 2",
                "active": true,
                "exclusionSchedules": []
              }
            ]
          }
          """);

    ObjectNode R =
      parseJson(
        """
          {
            "id": "schedule-1",
            "name": "Main Schedule",
            "exclusionSchedules": [
              {
                "id": "schedule-2",
                "name": "Exclusion 1 Modified",
                "active": true,
                "exclusionSchedules": []
              },
              {
                "id": "schedule-3",
                "name": "Exclusion 2",
                "active": false,
                "exclusionSchedules": []
              }
            ]
          }
          """);

    DiffConfig cfg =
      DiffConfig.builder().list("/exclusionSchedules", ListRule.id("id")).build();

    // ACT
    List<DiffEntry> diffs = DiffEngine.compare(L, R, cfg);

    // ASSERT
    assertThat(diffs)
      .containsExactlyInAnyOrder(
        changedEntry("/exclusionSchedules/{schedule-2}/name", "Exclusion 1", "Exclusion 1 Modified"),
        changedEntry("/exclusionSchedules/{schedule-3}/active", true, false));
  }
}
