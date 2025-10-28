# Mapping Configuration Guide

This guide explains **all configuration options** and how they interact. Use this as a reference while building diff configurations.

## Overview

PojoDiff compares two `JsonNode` trees and emits `DiffEntry { path, kind, old, new }` using **JSON Pointer** (RFC 6901) paths.
Configuration is applied at comparison time via four categories:

1. **List identity rules** - How to match elements in arrays
2. **Ignores** - Paths that should be skipped (exact, prefix, pattern, glob)
3. **Equivalences** - When two values should be treated as equal (exact, prefix, pattern, type, fallback)
4. **Other** - Type hints, cycle strategy, root path

---

## 1) List Identity Rules

Use a **field** or a **pointer** to identify each element of an array. This enables identity-based matching to track element additions, removals, and modifications accurately.

### Basic Usage

```java
// Match /items elements by "id" property
builder.list("/items", ListRule.id("id"));

// Match /tasks elements by nested pointer (starts with /)
builder.list("/tasks", ListRule.id("/metadata/taskId"));
```

### Why Identity Matters

**Without list rules** (index-based pairing):
```
Left:  [{id: "A", name: "Alice"}, {id: "B", name: "Bob"}]
Right: [{id: "B", name: "Bob"}, {id: "A", name: "Alice"}]

Result: 2 CHANGED entries (every element appears different due to reordering)
```

**With list rules** (identity-based pairing):
```
Left:  [{id: "A", name: "Alice"}, {id: "B", name: "Bob"}]
Right: [{id: "B", name: "Bob"}, {id: "A", name: "Alice"}]

Result: 0 CHANGED entries (elements matched by ID, order doesn't matter)
```

### Field-Based Matching

Use `ListRule.id(fieldName)` when the identifier is a direct property:

```java
// Simple ID field
.list("/users", ListRule.id("id"))

// Composite objects - use unique fields
.list("/products", ListRule.id("sku"))
.list("/orders", ListRule.id("orderId"))
```

### Pointer-Based Matching

Use `ListRule.id(pointer)` for nested identifiers (paths starting with `/`):

```java
// Nested identifier (JSON Pointer)
.list("/tasks", ListRule.id("/metadata/taskId"))

// Deep nesting
.list("/projects", ListRule.id("/identity/uuid"))
```

### Nested Arrays

For nested arrays, use the **normalized path** (with array indices/identity keys removed):

```java
// Example structure:
// {
//   "teams": [
//     {"id": "team-1", "members": [{"empId": "E001", "name": "Alice"}]},
//     {"id": "team-2", "members": [{"empId": "E002", "name": "Bob"}]}
//   ]
// }

DiffConfig config = DiffConfig.builder()
    // Configure parent array
    .list("/teams", ListRule.id("id"))

    // Configure nested array using normalized path (removes /0, /{team-1}, etc.)
    .list("/teams/members", ListRule.id("empId"))

    .build();
```

**Behavior:**
- Normalized paths remove array indices (`/0`, `/1`) and identity keys (`/{id}`)
- Example: `/teams/{team-1}/members` normalizes to `/teams/members`
- Example: `/teams/0/members` normalizes to `/teams/members`
- The same normalized rule applies to all team instances

### No Rule Configured

When no rule is set for an array path, elements are paired by **index**:

```java
// This config has no list rules
DiffConfig config = DiffConfig.builder().build();

// Arrays paired by index: [0] with [0], [1] with [1], etc.
```

**Notes:**
- Identifier keys are escaped for path stability (`~` → `~0`, `/` → `~1`)
- Duplicate IDs: Last occurrence wins during indexing (prefer unique identifiers)
- Nested arrays: Use normalized paths (e.g., `/parent/child` not `/parent/0/child`)

---

## 2) Ignores

Skip irrelevant paths during comparison. Any matching path is excluded from diff results.

### Exact Path Matching

```java
// Ignore specific paths
builder.ignore("/debug/token");
builder.ignore("/version");
builder.ignore("/lastModified");
```

**Use when:** You know the exact path to ignore.

### Prefix Matching

```java
// Ignore entire subtrees
builder.ignorePrefix("/metadata");      // Ignores /metadata, /metadata/foo, /metadata/bar/baz
builder.ignorePrefix("/internal");      // Ignores all paths starting with /internal
```

**Use when:** Ignoring entire sections of the tree.

### Pattern Matching (Regex)

```java
// Use Java regex patterns
builder.ignorePattern(Pattern.compile("^/temp/.*$"));
builder.ignorePattern(Pattern.compile("^/.*/debug$"));
```

**Use when:** Complex matching logic required.

### Glob Matching

```java
// Use glob wildcards (compiled to regex internally)
builder.ignoreGlob("/**/metadata/**");   // Any path containing /metadata/
builder.ignoreGlob("/**/temp/*");        // Matches /foo/temp/bar, /temp/anything
builder.ignoreGlob("/*/debug");          // Matches /foo/debug, /bar/debug
```

**Use when:** Glob syntax is more readable than regex.

**Glob Syntax:**
- `*` - Matches any characters except `/`
- `**` - Matches any characters including `/`
- `?` - Matches exactly one character

### Multiple Ignore Rules

All ignore rules are combined with OR logic:

```java
DiffConfig config = DiffConfig.builder()
    .ignore("/version")
    .ignorePrefix("/metadata")
    .ignorePattern(Pattern.compile("^/.*/temp$"))
    .ignoreGlob("/**/debug/**")
    .build();

// Path is ignored if it matches ANY of the above rules
```

---

## 3) Equivalences

Define custom equality semantics for specific paths or types. Precedence: **exact > pattern > prefix > type > fallback**.

### Exact Path Matching

```java
// Specific path
.equivalentAt("/price", Equivalences.numericWithin(0.01))
.equivalentAt("/email", Equivalences.caseInsensitive())
```

**Use when:** Targeting a single, known path.

### Pattern Matching (Regex)

```java
// Match multiple paths with regex
.equivalentPattern(
    Pattern.compile("^/.*/price$"),
    Equivalences.numericWithin(0.01)
)

// Complex pattern
.equivalentPattern(
    Pattern.compile("^/definitions/.*/tasks/.*/loggedAt$"),
    Equivalences.instantWithin(Duration.ofSeconds(1))
)
```

**Use when:** Applying the same equivalence to multiple similar paths.

### Glob Matching

```java
// Use glob wildcards (more readable than regex)
.equivalentGlob("/**/price", Equivalences.numericWithin(0.01))
.equivalentGlob("/**/timestamps/*", Equivalences.instantWithin(Duration.ofSeconds(1)))
.equivalentGlob("/items/*/name", Equivalences.caseInsensitive())
```

**Use when:** Glob syntax is more readable than regex for pattern matching.

**Glob Syntax:** Same as `ignoreGlob()` - `*` (any segment), `**` (any path), `?` (single char)

### Prefix Matching

```java
// All paths under a prefix
.equivalentUnder("/financials", Equivalences.numericWithin(0.001))
.equivalentUnder("/notes", Equivalences.punctuationQuestionEquals())
```

**Use when:** Applying equivalence to entire subtrees.

### Type-Scoped Equivalences

Requires **type hints** to be configured:

```java
// Apply to all fields of a specific type
.equivalentForType("java.time.Instant",
                   Equivalences.instantWithin(Duration.ofSeconds(1)))

.equivalentForType("java.time.ZonedDateTime",
                   Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.MILLIS))
```

**Use when:** Consistent behavior needed across all instances of a type.

**Type hints required:**
```java
// Auto-inference (recommended)
TypeHints hints = JacksonAdapters.inferTypeHints(RootClass.class, mapper);
builder.typeHints(hints.asMap());

// Manual hints
builder.typeHint("/createdAt", "java.time.Instant");
builder.typeHint("/items/*/updatedAt", "java.time.Instant");
```

### Fallback Equivalence

Applied when no other equivalence matches:

```java
// Default fallback
.equivalentFallback(Object::equals)

// Custom fallback
.equivalentFallback((left, right) -> {
    // Your custom default comparison
    return left.equals(right);
})
```

**Use when:** Providing a catch-all comparison for unmatched paths.

### Built-in Equivalences

#### Numeric Tolerance

```java
// Within 0.01 tolerance
.equivalentAt("/price", Equivalences.numericWithin(0.01))

// Within 0.001 tolerance for financial data
.equivalentUnder("/financials", Equivalences.numericWithin(0.001))
```

**Behavior:** Compares numeric values allowing small differences (handles `IntNode`, `LongNode`, `DoubleNode`).

#### Case-Insensitive Strings

```java
.equivalentAt("/email", Equivalences.caseInsensitive())
.equivalentUnder("/names", Equivalences.caseInsensitive())
```

**Behavior:** Compares strings ignoring case differences.

#### Punctuation-Insensitive Comparison

```java
.equivalentAt("/description", Equivalences.punctuationQuestionEquals())
```

**Behavior:** Compares strings while ignoring punctuation differences.

#### Time-Based Tolerance

```java
// Instant within 1 second
.equivalentForType("java.time.Instant",
                   Equivalences.instantWithin(Duration.ofSeconds(1)))

// OffsetDateTime within 1 minute
.equivalentForType("java.time.OffsetDateTime",
                   Equivalences.offsetDateTimeWithin(Duration.ofMinutes(1)))

// ZonedDateTime truncated to milliseconds
.equivalentForType("java.time.ZonedDateTime",
                   Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.MILLIS))
```

**Behavior:** Allows time-based differences within specified tolerance.

### Precedence Example

```java
DiffConfig config = DiffConfig.builder()
    // Priority 1: Exact path (highest)
    .equivalentAt("/items/0/price", Equivalences.numericWithin(0.001))

    // Priority 2: Pattern
    .equivalentPattern(Pattern.compile("^/.*/price$"),
                       Equivalences.numericWithin(0.01))

    // Priority 3: Prefix
    .equivalentUnder("/items", Equivalences.numericWithin(0.1))

    // Priority 4: Type
    .equivalentForType("java.lang.Double",
                       Equivalences.numericWithin(1.0))

    // Priority 5: Fallback (lowest)
    .equivalentFallback(Object::equals)

    .build();

// For path "/items/0/price":
// - Matches exact rule → uses 0.001 tolerance
// - Pattern, prefix, type, fallback are NOT evaluated
```

### Custom Equivalence Predicates

```java
// Custom BiPredicate
BiPredicate<JsonNode, JsonNode> customEquiv = (left, right) -> {
    if (left.isTextual() && right.isTextual()) {
        String leftNormalized = left.asText().trim().toLowerCase();
        String rightNormalized = right.asText().trim().toLowerCase();
        return leftNormalized.equals(rightNormalized);
    }
    return false;
};

.equivalentAt("/customField", customEquiv)
```

---

## 4) Other Configuration Options

### Type Hints

Map JSON Pointer paths to type keys for type-scoped equivalences:

```java
// Auto-inference from POJO (recommended)
TypeHints hints = JacksonAdapters.inferTypeHints(Order.class, mapper);
builder.typeHints(hints.asMap());

// Manual hints
builder.typeHint("/createdAt", "java.time.Instant");
builder.typeHint("/items/*/updatedAt", "java.time.Instant");
```

**Behavior:**
- Enables `equivalentForType()` matching
- Required for type-scoped equivalences to work

**Use within arrays:**
```java
// Type hint for all elements
builder.typeHint("/items/*/timestamp", "java.time.Instant");
```

### Root Path

Customize the initial JSON Pointer (default: `/`):

```java
// Custom root path
builder.rootPath("/__root")

// Default root
builder.rootPath("/")
```

**Use when:**
- You want to scope the diff under a specific prefix
- You need to strip a prefix in diff reporting

---

## Complete Configuration Example

```java
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.equivalence.Equivalences;
import io.github.pojotools.pojodiff.jackson.JacksonAdapters;

// Infer type hints from POJO structure
ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
TypeHints hints = JacksonAdapters.inferTypeHints(Order.class, mapper);

DiffConfig config = DiffConfig.builder()
    // List identity rules
    .list("/items", ListRule.id("sku"))
    .list("/items/*/reviews", ListRule.id("reviewId"))

    // Ignores
    .ignore("/version")
    .ignore("/lastModified")
    .ignorePrefix("/metadata")
    .ignoreGlob("/**/internal/**")

    // Equivalences - exact paths
    .equivalentAt("/total", Equivalences.numericWithin(0.01))
    .equivalentAt("/customerEmail", Equivalences.caseInsensitive())

    // Equivalences - patterns
    .equivalentPattern(
        Pattern.compile("^/.*/price$"),
        Equivalences.numericWithin(0.01)
    )

    // Equivalences - prefixes
    .equivalentUnder("/financials", Equivalences.numericWithin(0.001))

    // Type hints
    .typeHints(hints.asMap())

    // Equivalences - type-scoped
    .equivalentForType("java.time.Instant",
                       Equivalences.instantWithin(Duration.ofSeconds(1)))

    // Fallback equivalence
    .equivalentFallback(Object::equals)

    .build();
```

---

## FAQs

### Q: Pattern vs Glob?

**A:** `equivalentPattern()` and `ignorePattern()` use Java `Pattern` (regex). `equivalentGlob()` and `ignoreGlob()` accept wildcards (`*`, `?`, `**`) and compile to regex internally. Use globs for readability, patterns for complex logic.

### Q: What if multiple equivalence rules match?

**A:** The first matching rule in precedence order wins: exact > pattern > prefix > type > fallback.

### Q: What happens with duplicate IDs in lists?

**A:** The last occurrence wins during map-indexing. Prefer unique identity keys for reliable matching.

### Q: Can type-based rules work inside arrays?

**A:** Yes, as long as the **normalized path** has a `typeHint`. Normalized paths remove array indices and identity keys:

```java
// Configure type hint with normalized path (removes /0, /{id}, etc.)
.typeHint("/items/createdAt", "java.time.Instant")

// Type-scoped equivalence will apply to all matching paths
.equivalentForType("java.time.Instant",
                   Equivalences.instantWithin(Duration.ofSeconds(1)))

// This matches paths like:
//   /items/0/createdAt              → normalized to /items/createdAt
//   /items/{sku-123}/createdAt      → normalized to /items/createdAt
//   /items/{sku-456}/createdAt      → normalized to /items/createdAt
```

### Q: How do I debug configuration issues?

**A:** Start with a minimal config, then incrementally add rules. Print diff results at each step to understand the impact of each rule. See [OPERATIONS.md](OPERATIONS.md#troubleshooting) for debugging techniques.

---

## Related Documentation

- [OPERATIONS.md](OPERATIONS.md) - API reference and production best practices
- [ARCHITECTURE.md](ARCHITECTURE.md) - Design decisions and system architecture
- [PSEUDOCODE.md](PSEUDOCODE.md) - Algorithm flow and component design
- [README.md](../README.md) - Project overview and quick start
