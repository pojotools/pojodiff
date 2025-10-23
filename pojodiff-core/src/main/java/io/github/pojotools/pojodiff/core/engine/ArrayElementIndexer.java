package io.github.pojotools.pojodiff.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds identity-based indexes for JSON array elements to enable stable matching.
 *
 * <p><b>Single Responsibility:</b> Extracts identifier values from array elements according to a
 * ListRule and creates a map from identifier to JsonNode for efficient O(1) lookups during array
 * comparison.
 *
 * <p>Stateless utility class.
 */
final class ArrayElementIndexer {
  private ArrayElementIndexer() {}

  static Map<String, JsonNode> buildIndex(ArrayNode array, ListRule rule) {
    Map<String, JsonNode> out = new LinkedHashMap<>();
    for (JsonNode node : array) {
      if (isIndexableObject(node)) {
        addToIndex(out, (ObjectNode) node, rule);
      }
    }
    return out;
  }

  private static boolean isIndexableObject(JsonNode node) {
    return node.isObject();
  }

  private static void addToIndex(Map<String, JsonNode> index, ObjectNode obj, ListRule rule) {
    String id = extractId(obj, rule);
    String key = id != null ? id : "<null>";
    index.put(key, obj);
  }

  private static String extractId(ObjectNode obj, ListRule rule) {
    return rule.isPointer() ? extractIdByPointer(obj, rule) : extractIdByField(obj, rule);
  }

  private static String extractIdByPointer(ObjectNode obj, ListRule rule) {
    JsonNode node = PathUtils.at(obj, rule.identifierPath());
    return nodeToTextOrNull(node);
  }

  private static String extractIdByField(ObjectNode obj, ListRule rule) {
    JsonNode node = obj.get(rule.identifierPath());
    return nodeToTextOrNull(node);
  }

  private static String nodeToTextOrNull(JsonNode node) {
    return (node == null || node.isNull()) ? null : node.asText();
  }
}
