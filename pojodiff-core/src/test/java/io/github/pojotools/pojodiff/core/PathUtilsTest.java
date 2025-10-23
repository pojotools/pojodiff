package io.github.pojotools.pojodiff.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import org.junit.jupiter.api.Test;

/** Tests for PathUtils path normalization functionality. */
public class PathUtilsTest {

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  @Test
  void normalizesArrayIndices() {
    assertEquals("/items/name", PathUtils.normalizePathForTypeHint("/items/0/name"));
    assertEquals("/items/name", PathUtils.normalizePathForTypeHint("/items/123/name"));
    assertEquals(
        "/users/address/city", PathUtils.normalizePathForTypeHint("/users/5/address/city"));
  }

  @Test
  void normalizesIdentifierPatterns() {
    assertEquals("/items/name", PathUtils.normalizePathForTypeHint("/items/{id}/name"));
    assertEquals("/users/address", PathUtils.normalizePathForTypeHint("/users/{uuid}/address"));
    assertEquals("/data/value", PathUtils.normalizePathForTypeHint("/data/{key}/value"));
  }

  @Test
  void handlesNestedArrays() {
    assertEquals("/items/tags/value", PathUtils.normalizePathForTypeHint("/items/0/tags/1/value"));
    assertEquals("/matrix/data", PathUtils.normalizePathForTypeHint("/matrix/0/1/data"));
  }

  @Test
  void handlesRootPath() {
    assertEquals("/", PathUtils.normalizePathForTypeHint("/"));
    assertEquals("/", PathUtils.normalizePathForTypeHint(""));
    assertEquals("/", PathUtils.normalizePathForTypeHint(null));
  }

  @Test
  void preservesNonIndexSegments() {
    assertEquals("/items/name", PathUtils.normalizePathForTypeHint("/items/name"));
    assertEquals("/user/profile/email", PathUtils.normalizePathForTypeHint("/user/profile/email"));
  }

  @Test
  void handlesOnlyIndices() {
    assertEquals("/", PathUtils.normalizePathForTypeHint("/0"));
    assertEquals("/", PathUtils.normalizePathForTypeHint("/0/1/2"));
    assertEquals("/", PathUtils.normalizePathForTypeHint("/{id}"));
  }

  @Test
  void handlesMixedPatterns() {
    assertEquals(
        "/items/metadata/tags/value",
        PathUtils.normalizePathForTypeHint("/items/0/metadata/tags/1/value"));
    assertEquals(
        "/users/orders/items/name",
        PathUtils.normalizePathForTypeHint("/users/{userId}/orders/123/items/0/name"));
  }

  @Test
  void normalizesIdentifiersFromDiffEngine() {
    // Test that identifiers wrapped by DiffEngine are properly normalized
    assertEquals("/items/name", PathUtils.normalizePathForTypeHint("/items/{1}/name"));
    assertEquals("/tasks/value", PathUtils.normalizePathForTypeHint("/tasks/{2023-09-01}/value"));
    assertEquals(
        "/users/address/city",
        PathUtils.normalizePathForTypeHint("/users/{user-123}/address/city"));
  }

  @Test
  void atWithJsonPointerPath() {
    // Test at() with a path starting with "/"
    ObjectNode obj = JSON.objectNode();
    obj.set("nested", JSON.objectNode().put("id", "123"));

    JsonNode result = PathUtils.at(obj, "/nested/id");

    assertNotNull(result);
    assertEquals("123", result.asText());
  }

  @Test
  void atWithSimpleFieldName() {
    // Test at() with a simple field name (not starting with "/")
    // This covers line 26 - the escape path
    ObjectNode obj = JSON.objectNode().put("id", "abc");

    JsonNode result = PathUtils.at(obj, "id");

    assertNotNull(result);
    assertEquals("abc", result.asText());
  }

  @Test
  void atReturnsNullForMissingField() {
    ObjectNode obj = JSON.objectNode().put("name", "Alice");

    JsonNode result = PathUtils.at(obj, "/nonexistent");

    assertNull(result);
  }

  @Test
  void atHandlesSpecialCharactersInFieldName() {
    // Test field names with special characters that need escaping
    ObjectNode obj = JSON.objectNode().put("field/name", "value1").put("field~name", "value2");

    JsonNode result1 = PathUtils.at(obj, "field/name");
    JsonNode result2 = PathUtils.at(obj, "field~name");

    assertNotNull(result1);
    assertEquals("value1", result1.asText());
    assertNotNull(result2);
    assertEquals("value2", result2.asText());
  }

  // Branch coverage tests

  @Test
  void childWithBaseEndingInSlash() {
    String result = PathUtils.child("/items/", "name");
    assertEquals("/items/name", result);
  }

  @Test
  void childWithBaseNotEndingInSlash() {
    String result = PathUtils.child("/items", "name");
    assertEquals("/items/name", result);
  }

  @Test
  void childWithIndex() {
    String result = PathUtils.child("/items", 5);
    assertEquals("/items/5", result);
  }

  @Test
  void childWithSpecialCharactersInKey() {
    String result = PathUtils.child("/items", "field/name");
    // Should escape the / to ~1
    assertEquals("/items/field~1name", result);
  }

  @Test
  void normalizePrefixWithSlash() {
    String result = PathUtils.normalizePrefix("/metadata/");
    assertEquals("/metadata/", result);
  }

  @Test
  void normalizePrefixWithoutSlash() {
    String result = PathUtils.normalizePrefix("/metadata");
    assertEquals("/metadata/", result);
  }

  @Test
  void escapeHandlesTilde() {
    String result = PathUtils.escape("field~name");
    assertEquals("field~0name", result);
  }

  @Test
  void escapeHandlesSlash() {
    String result = PathUtils.escape("field/name");
    assertEquals("field~1name", result);
  }

  @Test
  void escapeHandlesBothTildeAndSlash() {
    String result = PathUtils.escape("~/path");
    assertEquals("~0~1path", result);
  }
}
