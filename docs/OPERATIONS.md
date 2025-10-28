# Operations Guide

This document covers operational aspects of using pojodiff in production environments, including API usage, performance optimization, configuration best practices, and troubleshooting.

## Related Documentation

- [MAPPING.md](MAPPING.md) - Complete configuration guide
- [ARCHITECTURE.md](ARCHITECTURE.md) - Design decisions and system architecture
- [PSEUDOCODE.md](PSEUDOCODE.md) - Algorithm flow and component design
- [README.md](../README.md) - Project overview and quick start guide

## API Entry Point

The primary API is the `PojoDiff` interface with the `PojoDiffCore` implementation:

```java
import io.github.pojotools.pojodiff.core.api.PojoDiff;
import io.github.pojotools.pojodiff.core.impl.PojoDiffCore;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Create differ instance
PojoDiff differ = new PojoDiffCore();

// Configure diff behavior
DiffConfig config = DiffConfig.builder()
    .list("/items", ListRule.idField("id"))
    .ignorePrefix("/metadata")
    .equivalentAt("/price", Equivalences.numericWithin(0.01))
    .build();

// Option 1: Compare POJOs directly (recommended)
ObjectMapper mapper = new ObjectMapper();
List<DiffEntry> diffs = differ.compare(oldObject, newObject, mapper, config);

// Option 2: Compare JsonNode trees if you already have them
JsonNode left = mapper.valueToTree(oldObject);
JsonNode right = mapper.valueToTree(newObject);
List<DiffEntry> diffs = differ.compare(left, right, config);

// Process results
for (DiffEntry diff : diffs) {
    System.out.printf("%s at %s: %s → %s%n",
        diff.kind(), diff.path(), diff.oldValue(), diff.newValue());
}
```

### DiffEntry Structure

Each diff entry contains:
- **path**: JSON Pointer (RFC 6901) to the changed location
- **kind**: `CHANGED`, `ADDED`, or `REMOVED`
- **oldValue**: Value in left tree (null for ADDED)
- **newValue**: Value in right tree (null for REMOVED)

## Configuration Best Practices

### Reuse Configuration Objects

`DiffConfig` is immutable and thread-safe. Cache and reuse across comparisons:

```java
// ✅ Good: Cache configuration and differ instance
private static final PojoDiff DIFFER = new PojoDiffCore();
private static final DiffConfig ORDER_CONFIG = DiffConfig.builder()
    .list("/items", ListRule.idField("sku"))
    .equivalentAt("/total", Equivalences.numericWithin(0.01))
    .equivalentGlob("/items/*/price", Equivalences.numericWithin(0.01))
    .build();

public List<DiffEntry> compareOrders(Order old, Order new) {
    JsonNode left = mapper.valueToTree(old);
    JsonNode right = mapper.valueToTree(new);
    return DIFFER.compare(left, right, ORDER_CONFIG);
}
```

```java
// ❌ Bad: Recreate configuration each time
public List<DiffEntry> compareOrders(Order old, Order new) {
    DiffConfig config = DiffConfig.builder() // Wasteful
        .list("/items", ListRule.idField("sku"))
        .build();
    // ...
}
```

### List Identity Rules

Use identity-based matching for arrays to track element changes:

```java
// Match by field name
.list("/items", ListRule.id("id"))

// Match by JSON Pointer (starts with /)
.list("/tasks", ListRule.id("/metadata/taskId"))
```

**Without list rules**, arrays are paired by index, which can produce misleading diffs when elements are reordered.

### Ignore Rules Hierarchy

Use the most specific ignore rule for clarity:

```java
// Exact path for single field
.ignore("/debug/token")

// Prefix for entire subtrees
.ignorePrefix("/metadata")

// Pattern for complex matching
.ignorePattern(Pattern.compile("^/.*/temp/.*$"))

// Glob for readability (compiled to regex)
.ignoreGlob("/**/metadata/**")
```

### Equivalence Rule Precedence

Rules are applied in order: **exact > pattern > prefix > type > fallback**

```java
DiffConfig config = DiffConfig.builder()
    // 1. Exact path (highest priority)
    .equivalentAt("/price", Equivalences.numericWithin(0.01))

    // 2. Pattern matching
    .equivalentPattern(Pattern.compile("^/.*/price$"),
                       Equivalences.numericWithin(0.01))

    // 3. Prefix matching
    .equivalentUnder("/financials", Equivalences.numericWithin(0.001))

    // 4. Type-scoped (requires type hints)
    .equivalentForType("java.time.Instant",
                       Equivalences.instantWithin(Duration.ofSeconds(1)))

    // 5. Fallback (lowest priority)
    .equivalentFallback(Object::equals)
    .build();
```

## Type Hints

Enable type-scoped equivalences by providing type information:

### Auto-Inference with Jackson

```java
import io.github.pojotools.pojodiff.jackson.JacksonAdapters;

// Infer type hints from POJO structure
TypeHints hints = JacksonAdapters.inferTypeHints(Order.class, mapper);

// Build config with hints
DiffConfig config = DiffConfig.builder()
    .typeHints(hints.asMap())
    .equivalentForType("java.time.Instant",
                       Equivalences.instantWithin(Duration.ofSeconds(1)))
    .build();
```

### Manual Type Hints

```java
DiffConfig config = DiffConfig.builder()
    .typeHint("/createdAt", "java.time.Instant")
    .typeHint("/items/*/updatedAt", "java.time.Instant")
    .equivalentForType("java.time.Instant",
                       Equivalences.instantWithin(Duration.ofSeconds(1)))
    .build();
```

## Built-in Equivalences

### Numeric Tolerance

```java
.equivalentAt("/price", Equivalences.numericWithin(0.01))
.equivalentUnder("/financials", Equivalences.numericWithin(0.001))
```

### String Comparison

```java
// Case-insensitive
.equivalentAt("/email", Equivalences.caseInsensitive())

// Ignore punctuation
.equivalentAt("/description", Equivalences.punctuationQuestionEquals())
```

### Time-Based Tolerance

```java
// Instant tolerance
.equivalentForType("java.time.Instant",
                   Equivalences.instantWithin(Duration.ofSeconds(1)))

// OffsetDateTime tolerance
.equivalentForType("java.time.OffsetDateTime",
                   Equivalences.offsetDateTimeWithin(Duration.ofMinutes(1)))

// ZonedDateTime truncation
.equivalentForType("java.time.ZonedDateTime",
                   Equivalences.zonedDateTimeTruncatedTo(ChronoUnit.MILLIS))
```

### Custom Equivalences

```java
// Custom predicate
BiPredicate<JsonNode, JsonNode> customEquiv = (left, right) -> {
    // Your custom comparison logic
    return left.asText().equalsIgnoreCase(right.asText());
};

.equivalentAt("/customField", customEquiv)
```

## Performance Optimization

### Hot Path Optimizations

pojodiff includes performance optimizations:

1. **TreeSet for field iteration** - Stable, deterministic ordering
2. **Direct node comparison** - Minimal object allocations
3. **Immutable configuration** - Thread-safe and reusable

### Comparison Patterns

```java
// ✅ Good: Batch comparisons with same config
PojoDiff differ = new PojoDiffCore();
List<ComparisonResult> results = new ArrayList<>();
for (Pair<Order, Order> pair : orderPairs) {
    JsonNode left = mapper.valueToTree(pair.old());
    JsonNode right = mapper.valueToTree(pair.new());
    List<DiffEntry> diffs = differ.compare(left, right, cachedConfig);
    results.add(new ComparisonResult(pair, diffs));
}
```

### ObjectMapper Reuse

Share `ObjectMapper` instances across conversions:

```java
// ✅ Good: Reuse mapper and differ
private static final ObjectMapper MAPPER = new ObjectMapper()
    .findAndRegisterModules(); // For Java 8 time types
private static final PojoDiff DIFFER = new PojoDiffCore();

public List<DiffEntry> compare(Object old, Object new) {
    JsonNode left = MAPPER.valueToTree(old);
    JsonNode right = MAPPER.valueToTree(new);
    return DIFFER.compare(left, right, config);
}
```

## Thread Safety

### Thread-Safe Components

- ✅ `DiffConfig` - Immutable after creation
- ✅ `ObjectMapper` - Thread-safe when properly configured
- ✅ `PojoDiff` implementations - Stateless comparison
- ✅ Built-in equivalence predicates - Thread-safe

### Recommended Patterns

```java
// ✅ Pattern 1: Shared config and differ (recommended)
private static final PojoDiff DIFFER = new PojoDiffCore();
private static final DiffConfig CONFIG = DiffConfig.builder().build();

@Async
public CompletableFuture<List<DiffEntry>> compareAsync(Object old, Object new) {
    return CompletableFuture.supplyAsync(() -> {
        JsonNode left = mapper.valueToTree(old);
        JsonNode right = mapper.valueToTree(new);
        return DIFFER.compare(left, right, CONFIG);
    });
}
```

## Determinism Guarantees

### Ordering Guarantees

pojodiff provides predictable ordering:

1. **Object fields**: Sorted alphabetically via `TreeSet`
2. **Array elements**: Deterministic based on list rules or index
3. **Diff entries**: Stable order based on tree traversal

### Reproducible Results

Given identical inputs and configuration:

- ✅ **Same diff entries** - Always produces identical results
- ✅ **Same ordering** - Deterministic field and array traversal
- ✅ **Same paths** - Consistent JSON Pointer generation

## Production Deployment Patterns

### Configuration Management

```java
@Configuration
public class DiffConfigProvider {

    @Bean("orderDiffConfig")
    public DiffConfig orderDiffConfig(ObjectMapper mapper) {
        TypeHints hints = JacksonAdapters.inferTypeHints(Order.class, mapper);

        return DiffConfig.builder()
            .typeHints(hints.asMap())
            .list("/items", ListRule.idField("sku"))
            .ignorePrefix("/metadata")
            .equivalentAt("/total", Equivalences.numericWithin(0.01))
            .equivalentGlob("/items/*/price", Equivalences.numericWithin(0.01))
            .equivalentForType("java.time.Instant",
                              Equivalences.instantWithin(Duration.ofSeconds(1)))
            .build();
    }

    @Bean("configDiffConfig")
    public DiffConfig configDiffConfig() {
        return DiffConfig.builder()
            .ignoreGlob("/**/metadata/**")
            .ignoreGlob("/**/version")
            .equivalentFallback(Object::equals)
            .build();
    }
}
```

### Monitoring and Observability

```java
@Service
public class MonitoredDiffService {
    private final PojoDiff differ = new PojoDiffCore();
    private final ObjectMapper mapper;
    private final DiffConfig config;
    private final MeterRegistry meterRegistry;

    public List<DiffEntry> compareWithMetrics(Object old, Object new) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            JsonNode left = mapper.valueToTree(old);
            JsonNode right = mapper.valueToTree(new);
            List<DiffEntry> diffs = differ.compare(left, right, config);

            // Record metrics
            sample.stop(Timer.builder("diff.comparison.duration")
                .tag("result", diffs.isEmpty() ? "identical" : "different")
                .register(meterRegistry));

            meterRegistry.counter("diff.comparison.count").increment();
            meterRegistry.counter("diff.changes.count").increment(diffs.size());

            return diffs;
        } catch (Exception e) {
            meterRegistry.counter("diff.comparison.errors").increment();
            throw e;
        }
    }
}
```

### Caching Strategy

```java
@Service
public class CachedDiffService {
    private final PojoDiff differ = new PojoDiffCore();
    private final LoadingCache<ConfigKey, DiffConfig> configCache;
    private final ObjectMapper mapper;

    public CachedDiffService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.configCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build(this::loadDiffConfig);
    }

    public List<DiffEntry> compare(Object old, Object new, String configName) {
        try {
            DiffConfig config = configCache.get(new ConfigKey(configName));
            JsonNode left = mapper.valueToTree(old);
            JsonNode right = mapper.valueToTree(new);
            return differ.compare(left, right, config);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to load config: " + configName, e);
        }
    }

    private DiffConfig loadDiffConfig(ConfigKey key) {
        // Load from configuration file or database
        return DiffConfig.builder().build();
    }
}
```

### Audit Trail

```java
@Service
public class AuditedDiffService {
    private final PojoDiff differ = new PojoDiffCore();
    private final ObjectMapper mapper;
    private final DiffConfig config;
    private final AuditLogger auditLogger;

    public List<DiffEntry> compareWithAudit(Object old, Object new, String userId) {
        JsonNode left = mapper.valueToTree(old);
        JsonNode right = mapper.valueToTree(new);
        List<DiffEntry> diffs = differ.compare(left, right, config);

        // Log audit trail
        AuditEvent event = AuditEvent.builder()
            .timestamp(Instant.now())
            .userId(userId)
            .action("DIFF_COMPARISON")
            .oldValue(left.toString())
            .newValue(right.toString())
            .changesCount(diffs.size())
            .changes(diffs.stream()
                .map(d -> String.format("%s at %s", d.kind(), d.path()))
                .collect(Collectors.toList()))
            .build();

        auditLogger.log(event);
        return diffs;
    }
}
```

## Troubleshooting

### Common Issues

#### Misleading Array Diffs

**Problem:** Array diffs show many changes when only elements were reordered.

**Solution:** Configure list identity rules:

```java
// Before: Index-based pairing
DiffConfig config = DiffConfig.builder().build();

// After: Identity-based pairing
DiffConfig config = DiffConfig.builder()
    .list("/items", ListRule.id("id"))
    .build();
```

#### False Positives for Numeric Fields

**Problem:** Small numeric differences (floating point precision) flagged as changes.

**Solution:** Use numeric tolerance:

```java
DiffConfig config = DiffConfig.builder()
    .equivalentAt("/price", Equivalences.numericWithin(0.01))
    .equivalentUnder("/financials", Equivalences.numericWithin(0.001))
    .build();
```

#### Excessive Diffs on Metadata Fields

**Problem:** Metadata changes (timestamps, versions) create noise.

**Solution:** Ignore irrelevant paths:

```java
DiffConfig config = DiffConfig.builder()
    .ignorePrefix("/metadata")
    .ignore("/version")
    .ignore("/lastModified")
    .build();
```

#### Missing List Element Changes

**Problem:** Changes within list elements not detected.

**Solution:** Ensure list identity keys are unique and stable:

```java
// ✅ Good: Stable, unique identifier
.list("/items", ListRule.id("sku"))

// ❌ Bad: Non-unique or changing identifier
.list("/items", ListRule.id("name")) // Names may not be unique
```

### Debugging Techniques

#### Inspect JSON Trees

```java
// Convert to JsonNode for inspection
PojoDiff differ = new PojoDiffCore();
ObjectMapper mapper = new ObjectMapper();
JsonNode left = mapper.valueToTree(oldObject);
JsonNode right = mapper.valueToTree(newObject);

// Pretty print for debugging
System.out.println("LEFT:\n" + left.toPrettyString());
System.out.println("RIGHT:\n" + right.toPrettyString());

// Run diff
List<DiffEntry> diffs = differ.compare(left, right, config);
diffs.forEach(System.out::println);
```

#### Step-by-Step Configuration

```java
// Create differ instance
PojoDiff differ = new PojoDiffCore();

// Start with minimal config
DiffConfig minimal = DiffConfig.builder().build();
List<DiffEntry> diffs1 = differ.compare(left, right, minimal);

// Add list rules
DiffConfig withLists = DiffConfig.builder()
    .list("/items", ListRule.idField("id"))
    .build();
List<DiffEntry> diffs2 = differ.compare(left, right, withLists);

// Add ignores
DiffConfig withIgnores = DiffConfig.builder()
    .list("/items", ListRule.idField("id"))
    .ignorePrefix("/metadata")
    .build();
List<DiffEntry> diffs3 = differ.compare(left, right, withIgnores);

// Compare results to understand impact
System.out.println("Minimal diffs: " + diffs1.size());
System.out.println("With list rules: " + diffs2.size());
System.out.println("With ignores: " + diffs3.size());
```

#### Custom Logging

```java
public class LoggingDiffService {
    private static final Logger log = LoggerFactory.getLogger(LoggingDiffService.class);
    private final PojoDiff differ = new PojoDiffCore();

    public List<DiffEntry> compareWithLogging(Object old, Object new, DiffConfig config) {
        log.debug("Starting comparison");

        JsonNode left = mapper.valueToTree(old);
        JsonNode right = mapper.valueToTree(new);

        log.debug("Left tree: {} nodes", countNodes(left));
        log.debug("Right tree: {} nodes", countNodes(right));

        List<DiffEntry> diffs = differ.compare(left, right, config);

        log.info("Comparison complete: {} differences found", diffs.size());
        diffs.forEach(diff ->
            log.debug("{} at {}: {} -> {}",
                diff.kind(), diff.path(), diff.oldValue(), diff.newValue()));

        return diffs;
    }

    private int countNodes(JsonNode node) {
        // Recursive node counting for diagnostics
        if (node == null || node.isValueNode()) return 1;
        if (node.isObject()) {
            int count = 1;
            node.fields().forEachRemaining(e -> count += countNodes(e.getValue()));
            return count;
        }
        if (node.isArray()) {
            int count = 1;
            for (JsonNode child : node) count += countNodes(child);
            return count;
        }
        return 1;
    }
}
```

## Integration Patterns

### API Testing

```java
@Test
public void testApiResponseChanges() {
    PojoDiff differ = new PojoDiffCore();
    
    // Fetch old and new API responses
    Order oldResponse = fetchOldApiVersion();
    Order newResponse = fetchNewApiVersion();

    // Configure acceptable differences
    DiffConfig config = DiffConfig.builder()
        .list("/items", ListRule.id("id"))
        .ignorePrefix("/metadata")
        .equivalentForType("java.time.Instant",
                          Equivalences.instantWithin(Duration.ofSeconds(1)))
        .build();

    // Compare
    JsonNode left = mapper.valueToTree(oldResponse);
    JsonNode right = mapper.valueToTree(newResponse);
    List<DiffEntry> diffs = differ.compare(left, right, config);

    // Assert no breaking changes
    assertTrue(diffs.isEmpty(), "API response changed: " + diffs);
}
```

### Configuration Auditing

```java
@Service
public class ConfigAuditService {
    private final PojoDiff differ = new PojoDiffCore();
    
    public void auditConfigChange(Config oldConfig, Config newConfig) {
        DiffConfig diffConfig = DiffConfig.builder()
            .ignoreGlob("/**/version")
            .ignoreGlob("/**/lastModified")
            .build();

        JsonNode left = mapper.valueToTree(oldConfig);
        JsonNode right = mapper.valueToTree(newConfig);
        List<DiffEntry> diffs = differ.compare(left, right, diffConfig);

        if (!diffs.isEmpty()) {
            logger.warn("Configuration changed: {} modifications", diffs.size());
            diffs.forEach(diff ->
                logger.info("  {} at {}: {} -> {}",
                    diff.kind(), diff.path(), diff.oldValue(), diff.newValue()));
        }
    }
}
```

### Data Synchronization

```java
@Service
public class SyncDetectionService {
    private final PojoDiff differ = new PojoDiffCore();
    
    public SyncStatus checkSyncStatus(Entity local, Entity remote) {
        DiffConfig config = DiffConfig.builder()
            .list("/items", ListRule.idField("id"))
            .equivalentForType("java.time.Instant",
                              Equivalences.instantWithin(Duration.ofSeconds(10)))
            .build();

        JsonNode left = mapper.valueToTree(local);
        JsonNode right = mapper.valueToTree(remote);
        List<DiffEntry> diffs = differ.compare(left, right, config);

        return new SyncStatus(
            diffs.isEmpty(),
            diffs.size(),
            diffs.stream()
                .map(DiffEntry::path)
                .collect(Collectors.toList())
        );
    }
}
```

## Summary

This operations guide covers the essential aspects of using pojodiff in production:

- **API usage patterns** with `PojoDiff` interface and `PojoDiffCore` implementation
- **Configuration best practices** for lists, ignores, and equivalences
- **Performance optimization** strategies
- **Thread safety** considerations
- **Production deployment** with monitoring, caching, and audit trails
- **Troubleshooting** techniques for common issues
- **Integration patterns** for testing, auditing, and synchronization

pojodiff provides a flexible, high-performance diff engine that adapts to your specific comparison needs through comprehensive configuration options. Combined with proper monitoring and caching strategies, it can reliably handle high-volume comparison workloads in enterprise environments.

For complete configuration details, see [MAPPING.md](MAPPING.md). For architecture and algorithm details, see [ARCHITECTURE.md](ARCHITECTURE.md) and [PSEUDOCODE.md](PSEUDOCODE.md).
