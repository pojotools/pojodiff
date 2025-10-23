# Architecture & Design Decisions

For detailed algorithm flow and component interactions, see **[PSEUDOCODE.md](PSEUDOCODE.md)**.

## Overview

pojodiff uses a **normalize → compare → report** pipeline with clean code principles and performance-optimized data structures.

### Core Pipeline (3 Stages)

1. **Normalization**: Validate configuration, resolve rules for paths
2. **Comparison**: Depth-first tree walk with identity-based cycle tracking
3. **Reporting**: Emit `DiffEntry` records with JSON Pointer paths

```
┌──────────────┐     ┌────────────────┐     ┌──────────────┐
│   DiffConfig │────→│   DiffEngine   │────→│  DiffEntry[] │
│  (immutable) │     │  (stateless)   │     │  (results)   │
└──────────────┘     └────────────────┘     └──────────────┘
       ↑                      ↑                      ↑
  Configuration           Comparison            Differences
  (list rules,            (depth-first          (path, kind,
   ignores,               traversal with        old, new)
   equivalences)          context)
```

## Key Design Decisions

### 1. Jackson-First Architecture

**Decision:** Operate on Jackson's `JsonNode` trees instead of raw POJOs.

**Rationale:**
- **Type safety:** JsonNode types (ObjectNode, ArrayNode, ValueNode) provide clear node semantics
- **Compatibility:** Works with existing Jackson configuration, custom serializers, annotations
- **Simplicity:** Avoid reflection in hot paths
- **Testability:** JSON trees are easy to construct and inspect in tests

**Trade-off:** Users must convert POJOs to JsonNode first, but this is typically a one-time cost.

### 2. Identity-Based List Matching

**Decision:** Use configurable identity fields/pointers to match array elements instead of index-based pairing.

**Rationale:**
- **Accuracy:** Tracks element additions, removals, and modifications correctly
- **Performance:** O(N) indexing via HashMap instead of O(N²) element-by-element comparison
- **Flexibility:** Supports both field-based (`idField`) and pointer-based (`idPointer`) matching

**Implementation:**
- `ArrayElementIndexer` builds a `Map<String, JsonNode>` from array elements
- Identity keys are extracted using configured rules
- Keys are escaped per JSON Pointer spec (`~` → `~0`, `/` → `~1`)

**Alternative considered:** Always use index-based pairing. Rejected due to poor accuracy with reordered arrays.

### 3. Equivalence Registry with Precedence

**Decision:** Support multiple equivalence rules with explicit precedence: exact > pattern > prefix > type > fallback.

**Rationale:**
- **Flexibility:** Different comparison semantics for different paths/types
- **Predictability:** Clear precedence avoids ambiguity
- **Extensibility:** Users can provide custom `BiPredicate<JsonNode, JsonNode>`

**Implementation:**
- `EquivalenceRegistry` resolves equivalences in precedence order
- Each category (exact, pattern, prefix, type) has dedicated lookup structures
- First match wins, subsequent rules not evaluated

**Alternative considered:** Flat list of rules evaluated in order. Rejected due to less clear semantics and harder debugging.

### 4. JSON Pointer Paths (RFC 6901)

**Decision:** Use JSON Pointer standard for all path strings.

**Rationale:**
- **Standard:** Well-defined specification (RFC 6901)
- **Interoperability:** Compatible with JSON Patch, JSON Schema
- **Tooling:** Libraries available for parsing and manipulation
- **Clarity:** Unambiguous path representation

**Escaping rules:**
- `~` → `~0`
- `/` → `~1`
- Identity keys wrapped in `{}` for distinction from field names

**Alternative considered:** Dot notation (e.g., `items.0.price`). Rejected due to ambiguity with field names containing dots.

## Component Architecture

### Core Components

#### DiffEngine

**Purpose:** Main entry point for diff comparison. Stateless and thread-safe.

**Responsibilities:**
- Orchestrate depth-first tree traversal
- Delegate to comparison logic based on node types
- Accumulate diff entries during traversal

**Key Methods:**
- `compare(left, right, config)` - Static entry point
- `walk(path, left, right, context)` - Recursive traversal
- `diffObject()`, `diffArray()` - Type-specific comparison

**Design Principles:**
- **≤4 parameters:** Uses `Context` object to carry state
- **Small methods:** 4-6 lines target with focused helpers
- **Single Responsibility:** Each method handles one comparison aspect

#### DiffConfig

**Purpose:** Immutable configuration facade aggregating all comparison rules.

**Responsibilities:**
- List identity rules (`PathToListRuleRegistry`)
- Ignore rules (`PathIgnoreFilter`)
- Equivalence rules (`EquivalenceRegistry`)
- Type hints mapping
- Cycle strategy
- Root path

**Key Design:**
- **Builder pattern:** Fluent API for configuration construction
- **Immutable:** Safe to share across threads and comparisons
- **Validation:** Input validation in builder methods
- **Thread-safe:** No mutable state after construction

**Alternative considered:** Mutable configuration. Rejected due to thread-safety concerns.

#### Context (inner class)

**Purpose:** Carry state during tree traversal without passing many parameters.

**Responsibilities:**
- Hold reference to `DiffConfig`
- Accumulate `DiffEntry` results

**Key Design:**
- **Private inner class:** Implementation detail of DiffEngine
- **Parameter reduction:** Avoids passing config + diffs separately
- **Lifecycle:** Created per `compare()` call, discarded after

### Configuration Components

#### PathToListRuleRegistry

**Purpose:** Efficient lookup of list rules by JSON Pointer path.

**Implementation:**
- `Map<String, ListRule>` for O(1) lookup
- Supports wildcards in paths (e.g., `/items/*/reviews`)

**Key Methods:**
- `getRuleForPath(pointer)` - Returns `Optional<ListRule>`

#### PathIgnoreFilter

**Purpose:** Determine if a path should be ignored during comparison.

**Implementation:**
- Exact match: `Set<String>` for O(1) lookup
- Prefix match: `List<String>` with linear scan (typically small)
- Pattern match: `List<Pattern>` with compiled regex

**Key Methods:**
- `shouldIgnore(pointer)` - Returns boolean

**Performance:**
- Exact matches optimized with HashSet
- Patterns compiled once during config construction

#### EquivalenceRegistry

**Purpose:** Resolve equivalence predicates for paths with precedence.

**Implementation:**
- Exact: `Map<String, BiPredicate>`
- Pattern: `List<PathPatternEquivalence>` with compiled patterns
- Prefix: `Map<String, BiPredicate>`
- Type: `Map<String, BiPredicate>`
- Fallback: Single `BiPredicate`

**Key Methods:**
- `resolve(pointer, typeKey)` - Returns `Optional<BiPredicate>`

**Resolution Order:**
1. Exact match on `pointer`
2. Pattern match (first matching pattern)
3. Prefix match (longest matching prefix)
4. Type match on `typeKey`
5. Fallback predicate

### Utility Components

#### ArrayElementIndexer

**Purpose:** Build identity-based index of array elements.

**Key Methods:**
- `buildIndex(array, rule)` - Returns `Map<String, JsonNode>`

**Implementation:**
- Extract identity value using `ListRule` (field or pointer)
- Escape keys per JSON Pointer spec
- Handle missing/null identity values (skip element)
- Last occurrence wins for duplicate IDs

#### PathUtils

**Purpose:** JSON Pointer path manipulation utilities.

**Key Methods:**
- `child(path, fieldName)` - Append field to path
- `child(path, index)` - Append array index to path
- `normalizePathForTypeHint(path)` - Strip identity keys for type lookup

**Implementation:**
- Handles escaping (`~0`, `~1`)
- Wraps identity keys in `{}`
- Provides consistent path formatting

#### GlobPatterns

**Purpose:** Convert glob patterns to compiled regex patterns.

**Key Methods:**
- `globToRegex(glob)` - Returns `Pattern`

**Implementation:**
- `*` → `[^/]*` (matches any non-slash)
- `**` → `.*` (matches any including slash)
- `?` → `.` (matches single character)
- Escape other regex metacharacters

### Equivalence Components

#### Equivalences

**Purpose:** Built-in equivalence predicates for common comparison scenarios.

**Provided Predicates:**
- `numericWithin(epsilon)` - Numeric tolerance
- `caseInsensitive()` - Case-insensitive strings
- `punctuationQuestionEquals()` - Ignore punctuation
- `instantWithin(duration)` - Time tolerance for Instant
- `offsetDateTimeWithin(duration)` - Time tolerance for OffsetDateTime
- `zonedDateTimeTruncatedTo(unit)` - Truncated ZonedDateTime

**Implementation:**
- Each returns `BiPredicate<JsonNode, JsonNode>`
- Handle type checking and null safety
- Optimized for common cases

## Clean Code Standards

The architecture adheres to strict clean code guidelines:

### Method Characteristics

- **≤4 parameters** - Complex parameter lists use context objects
- **≤1 indent level** - Guard clauses and early returns
- **~4-6 lines per method** - Small, focused functions
- **Positive conditionals** - `if (isValid())` not `if (!isInvalid())`

### Class Characteristics

- **Single Responsibility** - One reason to change
- **Immutability** - Config objects immutable after construction
- **Intention-revealing names** - Clear, descriptive names
- **No flag arguments** - Separate methods instead of boolean flags
- **Stateless when possible** - DiffEngine is fully stateless

### Example: Parameter Reduction

```java
// Before: 5 parameters (violation)
private static void walk(
    String path, JsonNode left, JsonNode right,
    DiffConfig config, List<DiffEntry> diffs) { ... }

// After: 4 parameters using context object
private record Context(DiffConfig config, List<DiffEntry> diffs, Set<Long> visited) {}

private static void walk(String path, JsonNode left, JsonNode right, Context ctx) { ... }
```

## Performance Characteristics

### Time Complexity

- **Overall:** O(N) where N = total nodes in tree
- **List matching:** O(M) where M = array size (identity-based indexing)
- **Equivalence resolution:** O(1) for exact, O(E) for patterns where E = pattern count
- **Ignore checking:** O(1) for exact, O(I) for prefixes/patterns where I = rule count

### Space Complexity

- **Context:** O(D) where D = tree depth (recursion stack)
- **List indices:** O(M) per array for identity maps
- **Configuration:** O(R) where R = total rules (cached and reused)

### Key Optimizations

1. **TreeSet for field names** - Provides deterministic, stable ordering
2. **Compiled patterns** - Regex patterns compiled once during config construction
3. **Direct node comparison** - Minimal object allocations during traversal
4. **Immutable configuration** - Thread-safe and reusable across comparisons

## Thread Safety

### Thread-Safe Components

- ✅ `DiffConfig` - Immutable after construction
- ✅ `DiffEngine` - Stateless static methods
- ✅ `Equivalences` - Built-in predicates are stateless
- ✅ `PathUtils` - Utility methods are stateless

### Per-Invocation State

- `Context` - Created per `compare()` call, not shared
- Visited set - Local to each comparison invocation
- Diff list - Accumulated locally, returned to caller

### Recommended Patterns

```java
// ✅ Good: Shared immutable config
private static final DiffConfig CONFIG = DiffConfig.builder().build();

public List<DiffEntry> compare(Object old, Object new) {
    JsonNode left = mapper.valueToTree(old);
    JsonNode right = mapper.valueToTree(new);
    return DiffEngine.compare(left, right, CONFIG);  // Thread-safe
}
```

## Determinism Guarantees

### Ordering Guarantees

1. **Object fields:** Sorted alphabetically via `TreeSet`
2. **Array elements:** Deterministic based on list rules or index
3. **Diff entries:** Stable order based on depth-first traversal

### Reproducible Results

Given identical inputs and configuration:
- ✅ **Same diff entries** - Always produces identical results
- ✅ **Same ordering** - Deterministic field and array traversal
- ✅ **Same paths** - Consistent JSON Pointer generation

## Correctness Guarantees

### Comparison Semantics

- **Null handling:** Explicit null checks before node operations
- **Type checking:** Validates node types before type-specific operations
- **Value comparison:** Uses `.equals()` for value comparison
- **Escape handling:** Proper JSON Pointer escaping for all keys

### Configuration Validation

- **Non-null contracts:** Builder methods validate non-null inputs
- **Path validation:** JSON Pointer paths validated on construction
- **Pattern compilation:** Regex patterns compiled and validated upfront

## Related Documentation

- [PSEUDOCODE.md](PSEUDOCODE.md) - Complete algorithm flow and component design
- [OPERATIONS.md](OPERATIONS.md) - API reference and production operations
- [MAPPING.md](MAPPING.md) - Configuration DSL specification
- [README.md](../README.md) - Project overview and quick start
