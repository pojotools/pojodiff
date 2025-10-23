package io.github.pojotools.pojodiff.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import io.github.pojotools.pojodiff.core.api.DiffKind;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Core diff engine that compares two JSON trees using the provided configuration. Call this
 * directly instead of going through the PojoDiff facade.
 */
public final class DiffEngine {
  private DiffEngine() {}

  public static List<DiffEntry> compare(JsonNode left, JsonNode right, DiffConfig config) {
    List<DiffEntry> diffs = new ArrayList<>();
    Context ctx = new Context(config, diffs);
    walk(ctx.config.rootPath(), left, right, ctx);
    return diffs;
  }

  private static void walk(String path, JsonNode left, JsonNode right, Context ctx) {
    if (shouldSkip(path, left, right, ctx)) {
      return;
    }

    if (hasLeafNode(left, right)) {
      ctx.add(createChange(path, left, right));
      return;
    }

    if (bothAreContainers(left, right)) {
      compareContainers(path, left, right, ctx);
      return;
    }

    ctx.add(createChange(path, left, right));
  }

  private static boolean shouldSkip(String path, JsonNode left, JsonNode right, Context ctx) {
    return isIgnoredPath(path, ctx)
        || areIdentical(left, right)
        || areEquivalent(path, left, right, ctx);
  }

  private static boolean isIgnoredPath(String path, Context ctx) {
    return ctx.config.isIgnored(path);
  }

  private static boolean areIdentical(JsonNode left, JsonNode right) {
    return left == right; // NOPMD - CompareObjectsWithEquals - identity check is intentional
  }

  private static boolean areEquivalent(String path, JsonNode left, JsonNode right, Context ctx) {
    return areEqual(left, right) || matchesCustomEquivalence(path, left, right, ctx.config);
  }

  private static boolean areEqual(JsonNode left, JsonNode right) {
    if (left == null || right == null) {
      return false;
    }
    if (left.isValueNode() && right.isValueNode()) {
      return Objects.equals(left, right);
    }
    return false;
  }

  private static boolean matchesCustomEquivalence(
      String path, JsonNode left, JsonNode right, DiffConfig config) {
    return config.equivalenceAt(path).map(eq -> eq.test(left, right)).orElse(false);
  }

  private static boolean hasLeafNode(JsonNode left, JsonNode right) {
    return isLeaf(left) || isLeaf(right);
  }

  private static boolean isLeaf(JsonNode node) {
    return node == null || node.isValueNode() || node.isMissingNode() || node.isNull();
  }

  private static DiffEntry createChange(String path, JsonNode oldValue, JsonNode newValue) {
    return new DiffEntry(path, DiffKind.CHANGED, oldValue, newValue);
  }

  private static boolean bothAreContainers(JsonNode left, JsonNode right) {
    return (left.isObject() && right.isObject()) || (left.isArray() && right.isArray());
  }

  private static void compareContainers(String path, JsonNode left, JsonNode right, Context ctx) {
    if (left.isObject() && right.isObject()) {
      diffObject(path, (ObjectNode) left, (ObjectNode) right, ctx);
    } else if (left.isArray() && right.isArray()) {
      diffArray(path, (ArrayNode) left, (ArrayNode) right, ctx);
    }
  }

  private static void diffObject(String path, ObjectNode left, ObjectNode right, Context ctx) {
    Set<String> fieldNames = collectFieldNames(left, right);
    for (String name : fieldNames) {
      compareObjectField(path, name, left, right, ctx);
    }
  }

  private static Set<String> collectFieldNames(ObjectNode left, ObjectNode right) {
    Set<String> names = new TreeSet<>();
    left.fieldNames().forEachRemaining(names::add);
    right.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static void compareObjectField(
      String path, String fieldName, ObjectNode left, ObjectNode right, Context ctx) {
    String childPath = PathUtils.child(path, fieldName);
    walk(childPath, left.get(fieldName), right.get(fieldName), ctx);
  }

  private static void diffArray(String path, ArrayNode left, ArrayNode right, Context ctx) {
    ListRule rule = ctx.config.listRule(path).orElse(ListRule.none());
    if (rule.isNone()) {
      pairByIndex(path, left, right, ctx);
      return;
    }
    pairById(path, left, right, ctx, rule);
  }

  private static void pairByIndex(String path, ArrayNode left, ArrayNode right, Context ctx) {
    int maxSize = Math.max(left.size(), right.size());
    for (int i = 0; i < maxSize; i++) {
      compareArrayElementByIndex(path, i, left, right, ctx);
    }
  }

  private static void compareArrayElementByIndex(
      String path, int index, ArrayNode left, ArrayNode right, Context ctx) {
    String childPath = PathUtils.child(path, index);
    JsonNode leftNode = getElementAt(left, index);
    JsonNode rightNode = getElementAt(right, index);
    comparePairedNodes(childPath, leftNode, rightNode, ctx);
  }

  private static JsonNode getElementAt(ArrayNode array, int index) {
    return index < array.size() ? array.get(index) : null;
  }

  private static void comparePairedNodes(String path, JsonNode left, JsonNode right, Context ctx) {
    if (left == null) {
      recordAddition(path, right, ctx);
      return;
    }
    if (right == null) {
      recordRemoval(path, left, ctx);
      return;
    }
    walk(path, left, right, ctx);
  }

  private static void recordAddition(String path, JsonNode value, Context ctx) {
    ctx.add(new DiffEntry(path, DiffKind.ADDED, null, value));
  }

  private static void recordRemoval(String path, JsonNode value, Context ctx) {
    ctx.add(new DiffEntry(path, DiffKind.REMOVED, value, null));
  }

  private static void pairById(
      String path, ArrayNode left, ArrayNode right, Context ctx, ListRule rule) {
    Map<String, JsonNode> leftIndex = ArrayElementIndexer.buildIndex(left, rule);
    Map<String, JsonNode> rightIndex = ArrayElementIndexer.buildIndex(right, rule);
    compareIndexedElements(path, leftIndex, rightIndex, ctx);
  }

  private static void compareIndexedElements(
      String path, Map<String, JsonNode> leftIndex, Map<String, JsonNode> rightIndex, Context ctx) {
    Set<String> allKeys = combinedKeys(leftIndex, rightIndex);
    for (String key : allKeys) {
      compareElementsByKey(path, key, leftIndex, rightIndex, ctx);
    }
  }

  private static Set<String> combinedKeys(
      Map<String, JsonNode> leftIndex, Map<String, JsonNode> rightIndex) {
    Set<String> keys = new TreeSet<>();
    keys.addAll(leftIndex.keySet());
    keys.addAll(rightIndex.keySet());
    return keys;
  }

  private static void compareElementsByKey(
      String path,
      String key,
      Map<String, JsonNode> leftIndex,
      Map<String, JsonNode> rightIndex,
      Context ctx) {
    // Wrap the identifier key in {} to distinguish it from field names
    // This allows PathUtils.normalizePathForTypeHint to correctly identify and remove identifiers
    String childPath = PathUtils.child(path, "{" + key + "}");
    JsonNode leftNode = leftIndex.get(key);
    JsonNode rightNode = rightIndex.get(key);
    comparePairedNodes(childPath, leftNode, rightNode, ctx);
  }

  private static final class Context {
    final DiffConfig config;
    final List<DiffEntry> diffs;

    Context(DiffConfig config, List<DiffEntry> diffs) {
      this.config = config;
      this.diffs = diffs;
    }

    void add(DiffEntry e) {
      diffs.add(e);
    }
  }
}
