package io.github.pojotools.pojodiff.jackson;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuration for type hint inference from Jackson-annotated classes.
 *
 * <p><b>Single Responsibility:</b> Defines recursion depth limits for self-referential
 * types during type hint inference, with global defaults and per-path overrides.
 *
 * <p>Immutable after construction via builder. Thread-safe.
 */
public final class TypeHintInferenceConfig {
  private static final int DEFAULT_RECURSION_DEPTH = 1;

  private final int defaultMaxDepth;
  private final Map<String, Integer> prefixPathDepths;
  private final Map<Pattern, Integer> patternPathDepths;

  private TypeHintInferenceConfig(Builder builder) {
    this.defaultMaxDepth = builder.defaultMaxDepth;
    this.prefixPathDepths = Map.copyOf(builder.prefixPathDepths);
    this.patternPathDepths = Map.copyOf(builder.patternPathDepths);
  }

  /**
   * Creates a builder with conservative defaults (stop at first self-reference).
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a config that stops at the first self-reference (depth 0).
   * The most conservative option - no recursion into self-referential types.
   */
  public static TypeHintInferenceConfig stopAtFirstReference() {
    return builder().defaultMaxDepth(0).build();
  }

  /**
   * Creates a config that allows one level of recursion (depth 1).
   * Good balance for most use cases.
   */
  public static TypeHintInferenceConfig defaultConfig() {
    return builder().build();
  }

  /**
   * Creates a config that allows unlimited recursion.
   * WARNING: May cause stack overflow or infinite loops for cyclic types!
   * Only use this when you are certain types are not self-referential.
   */
  public static TypeHintInferenceConfig unlimited() {
    return builder().defaultMaxDepth(Integer.MAX_VALUE).build();
  }

  /**
   * Returns the maximum recursion depth for a given JSON Pointer path.
   * Checks prefix matches (longest first for specificity), then patterns, finally default.
   */
  public int maxDepthForPath(String jsonPointer) {
    Integer depth = findPrefixMatchDepth(jsonPointer);
    if (depth != null) {
      return depth;
    }

    depth = findPatternMatchDepth(jsonPointer);
    if (depth != null) {
      return depth;
    }

    return defaultMaxDepth;
  }

  private Integer findPrefixMatchDepth(String jsonPointer) {
    String longestPrefix = null;
    Integer depthForLongestPrefix = null;

    for (Map.Entry<String, Integer> entry : prefixPathDepths.entrySet()) {
      if (isMoreSpecificMatch(jsonPointer, entry.getKey(), longestPrefix)) {
        longestPrefix = entry.getKey();
        depthForLongestPrefix = entry.getValue();
      }
    }

    return depthForLongestPrefix;
  }

  private boolean isMoreSpecificMatch(String path, String candidatePrefix, String currentLongest) {
    return pathStartsWith(path, candidatePrefix) && isLongerPrefix(candidatePrefix, currentLongest);
  }

  private boolean pathStartsWith(String path, String prefix) {
    return path.startsWith(prefix);
  }

  private boolean isLongerPrefix(String candidatePrefix, String currentLongest) {
    return currentLongest == null || candidatePrefix.length() > currentLongest.length();
  }

  private Integer findPatternMatchDepth(String jsonPointer) {
    for (Map.Entry<Pattern, Integer> entry : patternPathDepths.entrySet()) {
      if (entry.getKey().matcher(jsonPointer).matches()) {
        return entry.getValue();
      }
    }
    return null;
  }

  public int defaultMaxDepth() {
    return defaultMaxDepth;
  }

  /**
   * Builder for TypeHintInferenceConfig with fluent API.
   */
  public static final class Builder {
    private int defaultMaxDepth = DEFAULT_RECURSION_DEPTH;
    private final Map<String, Integer> prefixPathDepths = new HashMap<>();
    private final Map<Pattern, Integer> patternPathDepths = new HashMap<>();

    /**
     * Sets the default maximum recursion depth for all paths.
     *
     * @param depth 0 = stop at the first self-reference, 1 = one level deep, etc.
     */
    public Builder defaultMaxDepth(int depth) {
      validateDepth(depth);
      this.defaultMaxDepth = depth;
      return this;
    }

    private void validateDepth(int depth) {
      if (depth < 0) {
        throw new IllegalArgumentException("depth cannot be negative: " + depth);
      }
    }

    /**
     * Sets maximum recursion depth for all paths starting with the given prefix.
     * Use this for both exact matches (e.g., "/exclusionSchedules") and prefix matches (e.g., "/metadata").
     * When multiple prefixes match, the longest (most specific) prefix wins.
     *
     * @param prefix Path prefix (e.g., "/metadata" or "/exclusionSchedules")
     * @param depth Maximum recursion depth for matching paths
     */
    public Builder maxDepthForPrefix(String prefix, int depth) {
      validatePrefix(prefix);
      validateDepth(depth);
      prefixPathDepths.put(prefix, depth);
      return this;
    }

    private void validatePrefix(String prefix) {
      if (prefix == null) {
        throw new IllegalArgumentException("prefix cannot be null");
      }
      if (!prefix.isEmpty() && !prefix.startsWith("/")) {
        throw new IllegalArgumentException("prefix must start with '/' or be empty: " + prefix);
      }
    }

    /**
     * Sets maximum recursion depth for paths matching the given pattern.
     * Patterns are checked after prefix matches, so prefix rules take precedence.
     *
     * @param pattern Regex pattern to match paths
     * @param depth Maximum recursion depth for matching paths
     */
    public Builder maxDepthForPattern(Pattern pattern, int depth) {
      validatePattern(pattern);
      validateDepth(depth);
      patternPathDepths.put(pattern, depth);
      return this;
    }

    private void validatePattern(Pattern pattern) {
      if (pattern == null) {
        throw new IllegalArgumentException("pattern cannot be null");
      }
    }

    public TypeHintInferenceConfig build() {
      return new TypeHintInferenceConfig(this);
    }
  }
}
