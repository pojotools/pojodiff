package io.github.pojotools.pojodiff.core.config;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Defines how to match array elements by identifier for stable diff comparison.
 *
 * <p><b>Single Responsibility:</b> Specifies the identifier extraction strategy (field name vs.
 * JSON Pointer) and path for matching array elements across left and right JSON structures.
 *
 * <p>Immutable value object. Thread-safe.
 */
@Value
@Accessors(fluent = true)
public class ListRule {
  @NonNull String identifierPath;
  boolean pointer;

  private ListRule(String identifierPath, boolean pointer) {
    if (identifierPath == null) {
      throw new IllegalArgumentException("identifierPath cannot be null");
    }
    this.identifierPath = identifierPath;
    this.pointer = pointer;
  }

  public static ListRule none() {
    return new ListRule("", false);
  }

  /**
   * Creates a list rule with an identifier path that can be either a field name or JSON Pointer.
   * Automatically detects the type: paths starting with "/" are treated as JSON Pointers, all
   * others are treated as field names.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code id("id")} - field name
   *   <li>{@code id("/nested/id")} - JSON Pointer
   *   <li>{@code id("uuid")} - field name
   * </ul>
   *
   * @param path field name or JSON Pointer path
   * @return ListRule configured with the appropriate type
   */
  public static ListRule id(String path) {
    validatePath(path);
    return new ListRule(path, isPointerPath(path));
  }

  private static void validatePath(String path) {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("identifier path must not be null or empty");
    }
  }

  private static boolean isPointerPath(String path) {
    return path.startsWith("/");
  }

  public boolean isNone() {
    return identifierPath.isEmpty();
  }

  public boolean isPointer() {
    return pointer;
  }
}
