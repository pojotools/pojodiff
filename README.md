# pojodiff

[![Maven Central](https://img.shields.io/maven-central/v/io.github.pojotools/pojodiff-jackson.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.pojotools/pojodiff-jackson)
[![Build Status](https://github.com/pojotools/pojodiff/actions/workflows/ci.yml/badge.svg)](https://github.com/pojotools/pojodiff/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/Kyran121/a5d2142f2335c93ce3f8d5fb38b232bf/raw/pojodiff-coverage.json)](https://pojotools.github.io/pojodiff/coverage/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)

A high-performance, configurable diff engine for POJOs and JSON trees. Built with clean code principles and Jackson-first architecture for maximum compatibility and performance.

## What & Why

**pojodiff** compares two object structures (POJOs or JSON trees) and produces a detailed list of differences, perfect for:

- **API testing** - Validate response changes between versions
- **Configuration auditing** - Track config file modifications over time
- **Data synchronization** - Detect changes in distributed systems
- **Version control** - Build custom diff tools for structured data
- **Change detection** - Monitor object state changes in applications

### Key Features

- **Configurable comparison** - Per-path/type equivalence rules, custom predicates
- **Flexible ignoring** - Skip paths by exact match, prefix, pattern, or glob
- **Smart array matching** - Identity-based list pairing to track element movement
- **Type-aware equivalence** - Time tolerance, numeric epsilon, case-insensitive strings
- **JSON Pointer paths** - Standard RFC 6901 paths for precise change location
- **Performance** - Identity-based matching, no reflection in hot paths
- **Clean code** - SOLID principles, small methods, ≤4 params target
- **Production ready** - Thread-safe, immutable config, comprehensive testing

### Jackson-First Architecture

pojodiff operates on Jackson's `JsonNode` trees, ensuring compatibility with your existing Jackson configuration, custom serializers/deserializers, and annotations. Convert your POJOs to trees once, then compare with full type safety.

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>pojodiff-jackson</artifactId>
    <version>0.1.0</version>
</dependency>
```

For SPI extensions (optional):
```xml
<dependency>
    <groupId>io.github.pojotools</groupId>
    <artifactId>pojodiff-spi</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Define Your POJOs

```java
public record Order(
    String id,
    String customerId,
    BigDecimal total,
    List<LineItem> items,
    Instant createdAt
) {}

public record LineItem(
    String sku,
    int quantity,
    BigDecimal price
) {}
```

### 3. Configure and Compare

```java
import io.github.pojotools.pojodiff.core.api.PojoDiff;
import io.github.pojotools.pojodiff.core.impl.PojoDiffCore;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.equivalence.Equivalences;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();
mapper.findAndRegisterModules(); // For Java 8 time types

// Configure comparison behavior
DiffConfig config = DiffConfig.builder()
    // Match line items by SKU
    .list("/items", ListRule.id("sku"))

    // Ignore metadata changes
    .ignorePrefix("/metadata")
    .ignore("/createdAt")

    // Tolerate small price differences
    .equivalentAt("/total", Equivalences.numericWithin(0.01))
    .equivalentGlob("/items/*/price", Equivalences.numericWithin(0.01))

    // Tolerate timestamp differences
    .equivalentAt("/lastModified",
        Equivalences.instantWithin(Duration.ofSeconds(1)))

    .build();

// Create differ instance
PojoDiff differ = new PojoDiffCore();

// Load your domain objects
Order oldOrder = loadOldOrder();
Order newOrder = loadNewOrder();

// Compare POJOs directly (converts to JSON internally)
List<DiffEntry> diffs = differ.compare(oldOrder, newOrder, mapper, config);

// Or compare JSON trees if you already have them
JsonNode left = mapper.valueToTree(oldOrder);
JsonNode right = mapper.valueToTree(newOrder);
List<DiffEntry> diffs = differ.compare(left, right, config);

// Process results
for (DiffEntry diff : diffs) {
    System.out.printf("%s at %s: %s → %s%n",
        diff.kind(), diff.path(), diff.oldValue(), diff.newValue());
}
```

### Result

```
CHANGED at /customerId: "CUST-001" → "CUST-002"
ADDED at /items/2: null → {"sku":"SKU-003","quantity":1,"price":15.99}
REMOVED at /items/1: {"sku":"SKU-002","quantity":2,"price":29.99} → null
```

## Documentation Map

### For Users

- **[MAPPING.md](docs/MAPPING.md)** - Configuration guide (list rules, ignores, equivalences, type hints)
- **[OPERATIONS.md](docs/OPERATIONS.md)** - API usage, performance tuning, production patterns

### For Contributors

- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Design decisions and component architecture
- **[PSEUDOCODE.md](docs/PSEUDOCODE.md)** - Algorithm flow and detailed pseudocode
- **[DEVELOPMENT.md](docs/DEVELOPMENT.md)** - Build setup and code quality tools
- **[CONTRIBUTING.md](docs/CONTRIBUTING.md)** - Contribution guidelines
- **[RELEASE.md](docs/RELEASE.md)** - Release process

### Version History

- **[CHANGELOG.md](docs/CHANGELOG.md)** - Version history and release notes
- **[COVERAGE-BADGE-SETUP.md](docs/COVERAGE-BADGE-SETUP.md)** - Coverage badge configuration

## Configuration

For complete configuration options and examples, see **[docs/MAPPING.md](docs/MAPPING.md)**.

Quick overview:

### List Identity Rules

Match array elements by field or pointer to track additions/removals/modifications:

```java
// Match by field name
.list("/items", ListRule.id("id"))

// Match by JSON Pointer (starts with /)
.list("/tasks", ListRule.id("/metadata/taskId"))
```

### Ignore Rules

Skip irrelevant paths:

```java
.ignore("/debug/token")                // exact
.ignorePrefix("/metadata")             // prefix
.ignorePattern(Pattern.compile("^/temp/.*$"))  // regex
.ignoreGlob("/**/metadata/**")         // glob
```

### Equivalence Rules

Define custom equality semantics:

```java
// Exact path
.equivalentAt("/price", Equivalences.numericWithin(0.01))

// All matching paths (regex)
.equivalentPattern(Pattern.compile("^/.*/price$"),
                   Equivalences.numericWithin(0.01))

// All matching paths (glob - more readable)
.equivalentGlob("/**/price", Equivalences.numericWithin(0.01))

// Prefix
.equivalentUnder("/financials", Equivalences.numericWithin(0.001))

// Type-scoped (requires type hints)
.equivalentForType("java.time.Instant",
                   Equivalences.instantWithin(Duration.ofSeconds(1)))

// Fallback for all paths
.equivalentFallback(Object::equals)
```

### Built-in Equivalences

- `numericWithin(double epsilon)` - Numeric tolerance comparison
- `caseInsensitive()` - Case-insensitive string comparison
- `punctuationQuestionEquals()` - Ignores punctuation differences
- `instantWithin(Duration)` - Time-based tolerance for Instant strings
- `offsetDateTimeWithin(Duration)` - Time-based tolerance for OffsetDateTime strings
- `zonedDateTimeTruncatedTo(ChronoUnit)` - Truncated ZonedDateTime comparison

## Type Hints

Enable type-scoped equivalences by providing type information:

```java
import io.github.pojotools.pojodiff.jackson.JacksonAdapters;

// Auto-infer from POJO structure
TypeHints hints = JacksonAdapters.inferTypeHints(Order.class, mapper);

// Build config with hints
DiffConfig config = DiffConfig.builder()
    .typeHints(hints.asMap())
    .equivalentForType("java.time.Instant",
                       Equivalences.instantWithin(Duration.ofSeconds(1)))
    .build();
```

## Project Structure

- `pojodiff-core/` - Core diff engine and configuration
- `pojodiff-jackson/` - Jackson integration and type inference
- `pojodiff-spi/` - Extension interfaces (TypeHints, NodeTreeFactory)
- `pojodiff-examples/` - Usage examples and integration tests
- `pojodiff-benchmarks/` - JMH performance benchmarks
- `pojodiff-coverage/` - Aggregated test coverage reports

## Requirements

- **Java 21** or later
- **Jackson 2.17+** (jackson-databind)

## Performance Characteristics

- **List matching**: O(N) with identity-based indexing
- **Tree traversal**: O(nodes) depth-first walk
- **Memory**: Minimal allocations, identity-based cycle tracking
- **Thread safety**: Immutable config, stateless comparison

See **[docs/OPERATIONS.md](docs/OPERATIONS.md)** for performance tuning.

## Development

See **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** for complete development setup, build instructions, and code quality tools.

Quick start for contributors:

```bash
git clone https://github.com/pojotools/pojodiff.git
cd pojodiff
mvn clean verify
```

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Related Projects

- **[flat2pojo](https://github.com/pojotools/flat2pojo)** - Sister project for converting flat maps to nested POJOs
