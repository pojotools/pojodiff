package io.github.pojotools.pojodiff.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Resolves custom equivalence predicates for JSON values at specific paths.
 *
 * <p><b>Single Responsibility:</b> Applies precedence-based lookup to find the appropriate
 * equivalence predicate for a given path and type: exact path > pattern > prefix > type hint >
 * fallback.
 *
 * <p>Immutable after construction. Thread-safe.
 */
final class EquivalenceRegistry {
  private final Map<String, BiPredicate<JsonNode, JsonNode>> exact;
  private final List<PathPatternEquivalence> patterns;
  private final Map<String, BiPredicate<JsonNode, JsonNode>> prefixes;
  private final Map<String, BiPredicate<JsonNode, JsonNode>> byType;
  private final BiPredicate<JsonNode, JsonNode> fallback;

  EquivalenceRegistry(
      Map<String, BiPredicate<JsonNode, JsonNode>> exact,
      List<PathPatternEquivalence> patterns,
      Map<String, BiPredicate<JsonNode, JsonNode>> prefixes,
      Map<String, BiPredicate<JsonNode, JsonNode>> byType,
      BiPredicate<JsonNode, JsonNode> fallback) {
    this.exact = Map.copyOf(exact);
    this.patterns = List.copyOf(patterns);
    this.prefixes = Map.copyOf(prefixes);
    this.byType = Map.copyOf(byType);
    this.fallback = fallback;
  }

  Optional<BiPredicate<JsonNode, JsonNode>> resolve(String pointer, String typeKey) {
    return resolveExact(pointer)
        .or(() -> resolvePattern(pointer))
        .or(() -> resolvePrefix(pointer))
        .or(() -> resolveByType(typeKey))
        .or(this::resolveFallback);
  }

  private Optional<BiPredicate<JsonNode, JsonNode>> resolveExact(String pointer) {
    return Optional.ofNullable(exact.get(pointer));
  }

  private Optional<BiPredicate<JsonNode, JsonNode>> resolvePattern(String pointer) {
    return patterns.stream()
        .filter(ppe -> ppe.pattern().matcher(pointer).matches())
        .map(PathPatternEquivalence::equivalence)
        .findFirst();
  }

  private Optional<BiPredicate<JsonNode, JsonNode>> resolvePrefix(String pointer) {
    return prefixes.entrySet().stream()
        .filter(entry -> pointer.startsWith(PathUtils.normalizePrefix(entry.getKey())))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  private Optional<BiPredicate<JsonNode, JsonNode>> resolveByType(String typeKey) {
    return typeKey != null ? Optional.ofNullable(byType.get(typeKey)) : Optional.empty();
  }

  private Optional<BiPredicate<JsonNode, JsonNode>> resolveFallback() {
    return Optional.ofNullable(fallback);
  }

  /** Pair of (regex pattern, equivalence). Immutable record. */
  record PathPatternEquivalence(Pattern pattern, BiPredicate<JsonNode, JsonNode> equivalence) {
    PathPatternEquivalence {
      Objects.requireNonNull(pattern, "pattern cannot be null");
      Objects.requireNonNull(equivalence, "equivalence cannot be null");
    }
  }
}
