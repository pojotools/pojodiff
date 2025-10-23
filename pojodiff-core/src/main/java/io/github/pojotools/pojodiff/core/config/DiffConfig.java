package io.github.pojotools.pojodiff.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.pojotools.pojodiff.core.util.GlobPatterns;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Central configuration facade for JSON diff comparison behavior.
 *
 * <p><b>Single Responsibility:</b> Aggregates and provides unified access to all diff configuration
 * components (list rules, ignore filters, equivalence predicates, type hints, and root path).
 *
 * <p>Immutable after construction via builder. Thread-safe.
 */
public final class DiffConfig {
  private static final String DEFAULT_ROOT_PATH = "/";

  private final PathToListRuleRegistry listRules;
  private final PathIgnoreFilter ignores;
  private final EquivalenceRegistry equivalences;
  private final Map<String, String> typeHints;
  private final String rootPath;

  private DiffConfig(Builder b) {
    this.listRules = buildListRules(b);
    this.ignores = buildIgnoreRules(b);
    this.equivalences = buildEquivalenceRegistry(b);
    this.typeHints = Map.copyOf(b.typeHints);
    this.rootPath = b.rootPath;
  }

  private static PathToListRuleRegistry buildListRules(Builder b) {
    return new PathToListRuleRegistry(b.listRules);
  }

  private static PathIgnoreFilter buildIgnoreRules(Builder b) {
    return new PathIgnoreFilter(b.ignoreExact, b.ignorePrefixes, b.ignorePatterns);
  }

  private static EquivalenceRegistry buildEquivalenceRegistry(Builder b) {
    return new EquivalenceRegistry(
        b.equivalenceExact,
        b.equivalencePatterns,
        b.equivalencePrefixes,
        b.equivalenceByType,
        b.equivalenceFallback);
  }

  public Optional<ListRule> listRule(String pointer) {
    String normalizedPath = PathUtils.normalizePathForTypeHint(pointer);
    return listRules.getRuleForPath(normalizedPath);
  }

  public boolean isIgnored(String pointer) {
    return ignores.shouldIgnore(pointer);
  }

  public Optional<BiPredicate<JsonNode, JsonNode>> equivalenceAt(String pointer) {
    String normalizedPath = PathUtils.normalizePathForTypeHint(pointer);
    return equivalences.resolve(pointer, typeHints.get(normalizedPath));
  }

  public String rootPath() {
    return rootPath;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder directly manages collections without sub-builders. Enforces non-null contract on public
   * surfaces.
   */
  public static final class Builder {
    private final Map<String, ListRule> listRules = new HashMap<>();
    private final Set<String> ignoreExact = new HashSet<>();
    private final List<String> ignorePrefixes = new ArrayList<>();
    private final List<Pattern> ignorePatterns = new ArrayList<>();
    private final Map<String, BiPredicate<JsonNode, JsonNode>> equivalenceExact = new HashMap<>();
    private final List<EquivalenceRegistry.PathPatternEquivalence> equivalencePatterns =
        new ArrayList<>();
    private final Map<String, BiPredicate<JsonNode, JsonNode>> equivalencePrefixes =
        new HashMap<>();
    private final Map<String, BiPredicate<JsonNode, JsonNode>> equivalenceByType = new HashMap<>();
    private final Map<String, String> typeHints = new HashMap<>();
    private BiPredicate<JsonNode, JsonNode> equivalenceFallback = null;
    private String rootPath = DEFAULT_ROOT_PATH;

    // List rules
    public Builder list(String pointer, ListRule rule) {
      validatePointer(pointer, "pointer");
      Objects.requireNonNull(rule, "rule cannot be null");
      listRules.put(pointer, rule);
      return this;
    }

    // Ignores
    public Builder ignore(String pointer) {
      validatePointer(pointer, "pointer");
      ignoreExact.add(pointer);
      return this;
    }

    public Builder ignorePrefix(String prefix) {
      validatePointer(prefix, "prefix");
      ignorePrefixes.add(prefix);
      return this;
    }

    public Builder ignorePattern(Pattern pattern) {
      Objects.requireNonNull(pattern, "pattern cannot be null");
      ignorePatterns.add(pattern);
      return this;
    }

    public Builder ignoreGlob(String glob) {
      Objects.requireNonNull(glob, "glob cannot be null");
      ignorePatterns.add(GlobPatterns.globToRegex(glob));
      return this;
    }

    // Equivalences
    public Builder equivalentAt(String pointer, BiPredicate<JsonNode, JsonNode> eq) {
      validatePointer(pointer, "pointer");
      Objects.requireNonNull(eq, "equivalence predicate cannot be null");
      equivalenceExact.put(pointer, eq);
      return this;
    }

    public Builder equivalentUnder(String prefix, BiPredicate<JsonNode, JsonNode> eq) {
      validatePointer(prefix, "prefix");
      Objects.requireNonNull(eq, "equivalence predicate cannot be null");
      equivalencePrefixes.put(prefix, eq);
      return this;
    }

    public Builder equivalentPattern(Pattern pattern, BiPredicate<JsonNode, JsonNode> eq) {
      Objects.requireNonNull(pattern, "pattern cannot be null");
      Objects.requireNonNull(eq, "equivalence predicate cannot be null");
      equivalencePatterns.add(new EquivalenceRegistry.PathPatternEquivalence(pattern, eq));
      return this;
    }

    public Builder equivalentForType(String typeKey, BiPredicate<JsonNode, JsonNode> eq) {
      validateNonEmpty(typeKey, "typeKey");
      Objects.requireNonNull(eq, "equivalence predicate cannot be null");
      equivalenceByType.put(typeKey, eq);
      return this;
    }

    public Builder equivalentFallback(BiPredicate<JsonNode, JsonNode> eq) {
      Objects.requireNonNull(eq, "equivalence predicate cannot be null");
      equivalenceFallback = eq;
      return this;
    }

    // Types
    public Builder typeHint(String pointer, String typeKey) {
      validatePointer(pointer, "pointer");
      validateNonEmpty(typeKey, "typeKey");
      typeHints.put(pointer, typeKey);
      return this;
    }

    // Other
    public Builder rootPath(String root) {
      this.rootPath = normalizeRootPath(root);
      return this;
    }

    public DiffConfig build() {
      return new DiffConfig(this);
    }

    private static void validatePointer(String pointer, String paramName) {
      Objects.requireNonNull(pointer, paramName + " cannot be null");
      if (pointer.isEmpty()) {
        throw new IllegalArgumentException(paramName + " cannot be empty");
      }
    }

    private static void validateNonEmpty(String value, String paramName) {
      Objects.requireNonNull(value, paramName + " cannot be null");
      if (value.trim().isEmpty()) {
        throw new IllegalArgumentException(paramName + " cannot be blank");
      }
    }

    private static String normalizeRootPath(String root) {
      if (root == null || root.isBlank()) {
        return DEFAULT_ROOT_PATH;
      }
      return root;
    }
  }
}
