package io.github.pojotools.pojodiff.core.util;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class PathUtils {
  private PathUtils() {}

  public static String child(String base, String key) {
    return base.endsWith("/") ? base + escape(key) : base + "/" + escape(key);
  }

  public static String child(String base, int index) {
    return child(base, String.valueOf(index));
  }

  public static JsonNode at(ObjectNode obj, String pointerLike) {
    JsonPointer pointer = compilePointer(pointerLike);
    return getNodeOrNull(obj, pointer);
  }

  private static JsonPointer compilePointer(String pointerLike) {
    return pointerLike.startsWith("/")
        ? JsonPointer.compile(pointerLike)
        : JsonPointer.compile("/" + escape(pointerLike));
  }

  private static JsonNode getNodeOrNull(ObjectNode obj, JsonPointer pointer) {
    JsonNode node = obj.at(pointer);
    return node.isMissingNode() ? null : node;
  }

  /** Escapes a string for use in a JSON Pointer, according to RFC 6901. */
  public static String escape(String raw) {
    return raw.replace("~", "~0").replace("/", "~1");
  }

  /** Normalizes a prefix by ensuring it ends with a slash. */
  public static String normalizePrefix(String prefix) {
    return prefix.endsWith("/") ? prefix : prefix + "/";
  }

  /**
   * Normalizes a JSON Pointer path by removing array indices and identifiers. This converts
   * instance-specific paths to structure-based paths for type hint lookup.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code /items/0/name} → {@code /items/name}
   *   <li>{@code /items/{id}/name} → {@code /items/name}
   *   <li>{@code /users/123/address/city} → {@code /users/address/city}
   * </ul>
   *
   * @param path JSON Pointer path that may contain array indices or identifiers
   * @return normalized path with indices/identifiers removed
   */
  public static String normalizePathForTypeHint(String path) {
    if (isNullOrEmpty(path)) {
      return "/";
    }

    if (isRoot(path)) {
      return path;
    }

    String[] segments = path.split("/");
    StringBuilder normalized = buildNormalizedPath(segments);

    return normalized.isEmpty() ? "/" : normalized.toString();
  }

  private static boolean isNullOrEmpty(String path) {
    return path == null || path.isEmpty();
  }

  private static boolean isRoot(String path) {
    return "/".equals(path);
  }

  private static StringBuilder buildNormalizedPath(String[] segments) {
    StringBuilder normalized = new StringBuilder();

    for (String segment : segments) {
      if (isStructuralSegment(segment)) {
        normalized.append('/').append(segment);
      }
    }

    return normalized;
  }

  private static boolean isStructuralSegment(String segment) {
    return isNonEmpty(segment) && isNotArrayIndexOrIdentifier(segment);
  }

  private static boolean isNonEmpty(String segment) {
    return !segment.isEmpty();
  }

  private static boolean isNotArrayIndexOrIdentifier(String segment) {
    return !isNumericIndex(segment) && !isIdentifierPattern(segment);
  }

  private static boolean isNumericIndex(String segment) {
    return segment.matches("\\d+");
  }

  private static boolean isIdentifierPattern(String segment) {
    return segment.startsWith("{") && segment.endsWith("}");
  }
}
