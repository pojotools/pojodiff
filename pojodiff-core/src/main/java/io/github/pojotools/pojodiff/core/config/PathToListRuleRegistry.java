package io.github.pojotools.pojodiff.core.config;

import java.util.Map;
import java.util.Optional;

/**
 * Registry mapping JSON Pointer paths to their corresponding list matching rules.
 *
 * <p><b>Single Responsibility:</b> Stores and retrieves path-specific ListRule configurations that
 * define how array elements should be matched during diff comparison.
 *
 * <p>Immutable after construction. Thread-safe.
 */
final class PathToListRuleRegistry {
  private final Map<String, ListRule> byPath;

  PathToListRuleRegistry(Map<String, ListRule> byPath) {
    this.byPath = Map.copyOf(byPath);
  }

  Optional<ListRule> getRuleForPath(String pointer) {
    return Optional.ofNullable(byPath.get(pointer));
  }
}
