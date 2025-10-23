package io.github.pojotools.pojodiff.core.config;

import io.github.pojotools.pojodiff.core.util.PathUtils;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Determines whether a JSON Pointer path should be ignored during diff comparison.
 *
 * <p><b>Single Responsibility:</b> Evaluates paths against configured ignore rules (exact matches,
 * prefix patterns, and regex patterns) to filter out unwanted paths.
 *
 * <p>Immutable after construction. Thread-safe.
 */
final class PathIgnoreFilter {
  private final Set<String> exact;
  private final List<String> prefixes;
  private final List<Pattern> patterns;

  PathIgnoreFilter(Set<String> exact, List<String> prefixes, List<Pattern> patterns) {
    this.exact = Set.copyOf(exact);
    this.prefixes = List.copyOf(prefixes);
    this.patterns = List.copyOf(patterns);
  }

  boolean shouldIgnore(String pointer) {
    return isExactMatch(pointer) || matchesAnyPrefix(pointer) || matchesAnyPattern(pointer);
  }

  private boolean isExactMatch(String pointer) {
    return exact.contains(pointer);
  }

  private boolean matchesAnyPrefix(String pointer) {
    return prefixes.stream()
        .anyMatch(prefix -> pointer.startsWith(PathUtils.normalizePrefix(prefix)));
  }

  private boolean matchesAnyPattern(String pointer) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(pointer).matches());
  }
}
