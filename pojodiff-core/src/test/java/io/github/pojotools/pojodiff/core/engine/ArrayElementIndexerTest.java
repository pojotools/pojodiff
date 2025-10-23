package io.github.pojotools.pojodiff.core.engine;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.config.ListRule;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for ArrayElementIndexer identity-based indexing.
 */
public class ArrayElementIndexerTest {

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  @Test
  void buildsIndexWithSimpleIdField() {
    // ARRANGE
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("id", "1").put("name", "Alice"));
    array.add(JSON.objectNode().put("id", "2").put("name", "Bob"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKeys("1", "2");
    assertThat(index.get("1").get("name").asText()).isEqualTo("Alice");
    assertThat(index.get("2").get("name").asText()).isEqualTo("Bob");
  }

  @Test
  void buildsIndexWithJsonPointer() {
    // ARRANGE
    ArrayNode array = JSON.arrayNode();
    ObjectNode obj1 = JSON.objectNode();
    obj1.set("metadata", JSON.objectNode().put("id", "uuid-1"));
    obj1.put("value", "data1");
    ObjectNode obj2 = JSON.objectNode();
    obj2.set("metadata", JSON.objectNode().put("id", "uuid-2"));
    obj2.put("value", "data2");
    array.add(obj1);
    array.add(obj2);
    ListRule rule = ListRule.id("/metadata/id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKeys("uuid-1", "uuid-2");
    assertThat(index.get("uuid-1").get("value").asText()).isEqualTo("data1");
  }

  @Test
  void handlesNullIdValue() {
    // ARRANGE - element with null ID
    ArrayNode array = JSON.arrayNode();
    ObjectNode objWithNull = JSON.objectNode();
    objWithNull.putNull("id");
    objWithNull.put("name", "NoId");
    array.add(objWithNull);
    array.add(JSON.objectNode().put("id", "1").put("name", "WithId"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKey("<null>");
    assertThat(index).containsKey("1");
    assertThat(index.get("<null>").get("name").asText()).isEqualTo("NoId");
  }

  @Test
  void handlesMissingIdField() {
    // ARRANGE - element without the ID field
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("name", "NoIdField"));
    array.add(JSON.objectNode().put("id", "1").put("name", "WithId"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKey("<null>");
    assertThat(index).containsKey("1");
  }

  @Test
  void handlesDuplicateIds() {
    // ARRANGE - multiple elements with same ID (last one wins)
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("id", "dup").put("value", "first"));
    array.add(JSON.objectNode().put("id", "dup").put("value", "second"));
    array.add(JSON.objectNode().put("id", "dup").put("value", "third"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(1);
    assertThat(index).containsKey("dup");
    // Last one wins in LinkedHashMap
    assertThat(index.get("dup").get("value").asText()).isEqualTo("third");
  }

  @Test
  void skipsNonObjectElements() {
    // ARRANGE - array with mixed types
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("id", "1").put("name", "Object"));
    array.add("string value"); // Non-object
    array.add(123); // Non-object
    array.add(JSON.objectNode().put("id", "2").put("name", "AnotherObject"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    // Only objects should be indexed
    assertThat(index).hasSize(2);
    assertThat(index).containsOnlyKeys("1", "2");
  }

  @Test
  void buildsEmptyIndexForEmptyArray() {
    // ARRANGE
    ArrayNode array = JSON.arrayNode();
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).isEmpty();
  }

  @Test
  void buildsEmptyIndexForArrayWithoutObjects() {
    // ARRANGE - array with only primitives
    ArrayNode array = JSON.arrayNode();
    array.add("string");
    array.add(123);
    array.add(true);
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).isEmpty();
  }

  @Test
  void convertsNonTextIdToText() {
    // ARRANGE - ID field with numeric value
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("id", 123).put("name", "NumericId"));
    array.add(JSON.objectNode().put("id", true).put("name", "BooleanId"));
    ListRule rule = ListRule.id("id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKeys("123", "true");
    assertThat(index.get("123").get("name").asText()).isEqualTo("NumericId");
    assertThat(index.get("true").get("name").asText()).isEqualTo("BooleanId");
  }

  @Test
  void handlesNestedPointerPaths() {
    // ARRANGE
    ArrayNode array = JSON.arrayNode();
    ObjectNode obj1 = JSON.objectNode();
    obj1.set("data", JSON.objectNode().set("meta", JSON.objectNode().put("uuid", "deep-1")));
    ObjectNode obj2 = JSON.objectNode();
    obj2.set("data", JSON.objectNode().set("meta", JSON.objectNode().put("uuid", "deep-2")));
    array.add(obj1);
    array.add(obj2);
    ListRule rule = ListRule.id("/data/meta/uuid");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKeys("deep-1", "deep-2");
  }

  @Test
  void handlesMissingPointerPath() {
    // ARRANGE - pointer path doesn't exist in object
    ArrayNode array = JSON.arrayNode();
    array.add(JSON.objectNode().put("name", "NoMeta"));
    array.add(JSON.objectNode().set("meta", JSON.objectNode().put("id", "has-meta")));
    ListRule rule = ListRule.id("/meta/id");

    // ACT
    Map<String, JsonNode> index = ArrayElementIndexer.buildIndex(array, rule);

    // ASSERT
    assertThat(index).hasSize(2);
    assertThat(index).containsKeys("<null>", "has-meta");
  }
}
