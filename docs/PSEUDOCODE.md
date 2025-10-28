# PojoDiff: Algorithm Flow & Component Design

**Generated:** 2024-10-21
**For Architecture:** See [ARCHITECTURE.md](ARCHITECTURE.md)
**Modules:** pojodiff-core, pojodiff-jackson, pojodiff-spi

---

## HIGH-LEVEL OVERVIEW

**Input:** Two `JsonNode` trees (left, right) + `DiffConfig`
**Output:** `List<DiffEntry>` containing differences

**Core Pipeline (3 Steps):**
1. Validate and resolve configuration rules
2. Depth-first tree traversal with comparison logic
3. Emit diff entries with JSON Pointer paths

## MAIN ALGORITHM: compare(left, right, config)

```
ENTRY POINT: DiffEngine.compare(left, right, config)

1. CREATE context
   ctx = new Context(config, new ArrayList<>())
   └─ config: immutable DiffConfig
   └─ diffs: mutable List<DiffEntry> (accumulator)

2. START traversal from root
   walk(config.rootPath(), left, right, ctx)

3. RETURN accumulated diffs
   return ctx.diffs
```

## CORE ALGORITHM: walk(path, left, right, ctx)

```
walk(path, left, right, ctx):

STEP 1: Early exit checks
IF shouldSkip(path, left, right, ctx):
  RETURN  // Skip this path and subtree

STEP 2: Handle leaf nodes
IF hasLeafNode(left, right):
  ctx.add(createChange(path, left, right))
  RETURN

STEP 3: Handle containers (objects or arrays)
IF bothAreContainers(left, right):
  compareContainers(path, left, right, ctx)
  RETURN

STEP 4: Fallback - nodes are different types
ctx.add(createChange(path, left, right))
```

### shouldSkip(path, left, right, ctx)

```
shouldSkip(path, left, right, ctx):

CHECK 1: Ignored path?
IF ctx.config.isIgnored(path):
  RETURN true

CHECK 2: Identical references?
IF left == right:  // Identity check
  RETURN true

CHECK 3: Equivalent by custom rules?
IF areEquivalent(path, left, right, ctx):
  RETURN true

RETURN false
```

### areEquivalent(path, left, right, ctx)

```
areEquivalent(path, left, right, ctx):

// Check value equality first
IF areEqual(left, right):
  RETURN true

// Check custom equivalence rules
equivalence = ctx.config.equivalenceAt(path)
IF equivalence.isPresent():
  RETURN equivalence.get().test(left, right)

RETURN false
```

### areEqual(left, right)

```
areEqual(left, right):

IF left == null OR right == null:
  RETURN false

IF left.isValueNode() AND right.isValueNode():
  RETURN left.equals(right)  // JsonNode value equality

RETURN false
```

## OBJECT COMPARISON: diffObject(path, left, right, ctx)

```
diffObject(path, leftObj, rightObj, ctx):

STEP 1: Collect all field names
fieldNames = collectFieldNames(leftObj, rightObj)
  └─ Uses TreeSet for deterministic ordering

STEP 2: Compare each field recursively
FOR EACH fieldName IN fieldNames:
  childPath = PathUtils.child(path, fieldName)
  leftChild = leftObj.get(fieldName)   // null if missing
  rightChild = rightObj.get(fieldName)  // null if missing
  walk(childPath, leftChild, rightChild, ctx)
```

### collectFieldNames(left, right)

```
collectFieldNames(left, right):

names = new TreeSet<>()  // Sorted for determinism
left.fieldNames().forEachRemaining(names::add)
right.fieldNames().forEachRemaining(names::add)
RETURN names

Purpose: Alphabetically sorted field names for stable, reproducible diffs
```

## ARRAY COMPARISON: diffArray(path, left, right, ctx)

```
diffArray(path, leftArr, rightArr, ctx):

STEP 1: Look up list rule for path
rule = ctx.config.listRule(path).orElse(ListRule.none())

STEP 2: Branch by matching strategy
IF rule.isNone():
  pairByIndex(path, leftArr, rightArr, ctx)
ELSE:
  pairById(path, leftArr, rightArr, ctx, rule)
```

### pairByIndex(path, left, right, ctx)

```
pairByIndex(path, leftArr, rightArr, ctx):

// Simple index-based pairing
maxSize = max(leftArr.size(), rightArr.size())

FOR i FROM 0 TO maxSize - 1:
  childPath = PathUtils.child(path, i)
  leftElem = getElementAt(leftArr, i)   // null if out of bounds
  rightElem = getElementAt(rightArr, i)  // null if out of bounds
  comparePairedNodes(childPath, leftElem, rightElem, ctx)
```

### pairById(path, left, right, ctx, rule)

```
pairById(path, leftArr, rightArr, ctx, rule):

STEP 1: Build identity-based indices
leftIndex = ArrayElementIndexer.buildIndex(leftArr, rule)
  └─ Map<String, JsonNode> where key = escaped identity value
rightIndex = ArrayElementIndexer.buildIndex(rightArr, rule)

STEP 2: Collect all identity keys
allKeys = new TreeSet<>()  // Sorted for determinism
allKeys.addAll(leftIndex.keySet())
allKeys.addAll(rightIndex.keySet())

STEP 3: Compare elements by identity
FOR EACH key IN allKeys:
  // Wrap key in {} to distinguish from field names
  childPath = PathUtils.child(path, "{" + key + "}")
  leftElem = leftIndex.get(key)   // null if not in left
  rightElem = rightIndex.get(key)  // null if not in right
  comparePairedNodes(childPath, leftElem, rightElem, ctx)
```

### ArrayElementIndexer.buildIndex(array, rule)

```
buildIndex(array, rule):

index = new LinkedHashMap<>()  // Maintains insertion order

FOR EACH element IN array:
  identityValue = extractIdentityValue(element, rule)

  IF identityValue == null:
    CONTINUE  // Skip elements with missing identity

  escapedKey = escapePointerSegment(identityValue)
  index.put(escapedKey, element)  // Last wins for duplicates

RETURN index
```

### extractIdentityValue(element, rule)

```
extractIdentityValue(element, rule):

IF rule.hasFieldName():
  // Field-based identity
  fieldValue = element.get(rule.fieldName())
  RETURN fieldValue != null ? fieldValue.asText() : null

ELSE IF rule.hasPointer():
  // Pointer-based identity
  node = element.at(rule.pointer())  // JSON Pointer traversal
  RETURN node.isMissingNode() ? null : node.asText()

RETURN null
```

### escapePointerSegment(value)

```
escapePointerSegment(value):

// JSON Pointer escaping (RFC 6901)
escaped = value.replace("~", "~0")   // ~ → ~0 (must be first)
escaped = escaped.replace("/", "~1")  // / → ~1
RETURN escaped
```

### comparePairedNodes(path, left, right, ctx)

```
comparePairedNodes(path, leftNode, rightNode, ctx):

IF leftNode == null:
  // Element added in right
  ctx.add(new DiffEntry(path, ADDED, null, rightNode))
  RETURN

IF rightNode == null:
  // Element removed from left
  ctx.add(new DiffEntry(path, REMOVED, leftNode, null))
  RETURN

// Both exist - recurse
walk(path, leftNode, rightNode, ctx)
```

## CONFIGURATION RESOLUTION

### config.isIgnored(path)

```
isIgnored(path):

// Delegates to PathIgnoreFilter
RETURN ignoreFilter.shouldIgnore(path)
```

#### PathIgnoreFilter.shouldIgnore(path)

```
shouldIgnore(path):

// Check exact matches (O(1))
IF path IN exactSet:
  RETURN true

// Check prefix matches (O(P) where P = prefix count)
FOR EACH prefix IN prefixes:
  IF path.startsWith(prefix):
    RETURN true

// Check pattern matches (O(R) where R = pattern count)
FOR EACH pattern IN patterns:
  IF pattern.matcher(path).matches():
    RETURN true

RETURN false
```

### config.equivalenceAt(path)

```
equivalenceAt(path):

// Normalize path for type hint lookup (strip identity keys)
normalizedPath = PathUtils.normalizePathForTypeHint(path)
typeKey = typeHints.get(normalizedPath)

// Delegates to EquivalenceRegistry
RETURN equivalenceRegistry.resolve(path, typeKey)
```

#### EquivalenceRegistry.resolve(path, typeKey)

```
resolve(path, typeKey):

// Priority 1: Exact match (O(1))
IF path IN exactMap:
  RETURN Optional.of(exactMap.get(path))

// Priority 2: Pattern match (O(P) where P = pattern count)
FOR EACH patternEntry IN patternList:
  IF patternEntry.pattern.matcher(path).matches():
    RETURN Optional.of(patternEntry.equivalence)

// Priority 3: Prefix match (O(N) where N = prefix count, typically small)
longestPrefix = null
longestLength = 0
FOR EACH (prefix, equiv) IN prefixMap:
  IF path.startsWith(prefix) AND prefix.length() > longestLength:
    longestPrefix = prefix
    longestLength = prefix.length()

IF longestPrefix != null:
  RETURN Optional.of(prefixMap.get(longestPrefix))

// Priority 4: Type match (O(1))
IF typeKey != null AND typeKey IN typeMap:
  RETURN Optional.of(typeMap.get(typeKey))

// Priority 5: Fallback
IF fallback != null:
  RETURN Optional.of(fallback)

RETURN Optional.empty()
```

### config.listRule(path)

```
listRule(path):

// Delegates to PathToListRuleRegistry
RETURN listRuleRegistry.getRuleForPath(path)
```

#### PathToListRuleRegistry.getRuleForPath(path)

```
getRuleForPath(path):

// Normalize the path first (removes array indices and identifiers)
// Example: /items/0/name -> /items/name
normalizedPath = normalizePathForTypeHint(path)

// Direct O(1) lookup using normalized path
IF normalizedPath IN rulesMap:
  RETURN Optional.of(rulesMap.get(normalizedPath))

RETURN Optional.empty()
```

## UTILITY FUNCTIONS

### PathUtils.child(path, segment)

```
child(path, segment):

// Append field name or array index to path
IF segment is String:
  escapedSegment = escapePointerSegment(segment)
  RETURN path + "/" + escapedSegment

ELSE IF segment is int:
  RETURN path + "/" + segment

ELSE IF segment starts with "{":
  // Identity key - already wrapped
  RETURN path + "/" + segment
```

### PathUtils.normalizePathForTypeHint(path)

```
normalizePathForTypeHint(path):

// Remove array indices and identifier patterns from path segments
// Converts instance-specific paths to structure-based paths
// Examples:
//   /items/0/name → /items/name
//   /items/{id}/name → /items/name
//   /users/123/address/city → /users/address/city

// Handle edge cases
IF path is null OR path is empty:
  RETURN "/"

IF path equals "/":
  RETURN path

// Split path into segments
segments = path.split("/")
normalized = empty StringBuilder

// Filter segments
FOR EACH segment IN segments:
  // Skip empty segments (from leading slash)
  IF segment.isEmpty():
    CONTINUE

  // Skip numeric indices (e.g., "0", "123")
  IF segment matches "\\d+":
    CONTINUE

  // Skip identifier patterns (e.g., "{id}", "{uuid}")
  IF segment.startsWith("{") AND segment.endsWith("}"):
    CONTINUE

  // Keep structural segments
  normalized.append("/").append(segment)

// Return "/" if no segments remain, otherwise return normalized path
RETURN normalized.isEmpty() ? "/" : normalized.toString()
```

## PERFORMANCE CHARACTERISTICS

### Time Complexity

- **Overall:** O(N) where N = total nodes in both trees
- **Object fields:** O(F log F) for TreeSet sorting where F = unique field names
- **Array pairing by index:** O(M) where M = max array size
- **Array pairing by ID:** O(M) for HashMap indexing
- **Equivalence resolution:** O(1) for exact, O(E) for patterns where E = equivalence count
- **Ignore checking:** O(1) for exact, O(I) for patterns where I = ignore count

### Space Complexity

- **Recursion stack:** O(D) where D = max tree depth
- **Array indices:** O(M) per array for identity maps
- **Context:** O(1) (references only)

### Hot Paths

1. **walk()** - Called once per node pair
2. **shouldSkip()** - Called once per node pair
3. **config.isIgnored()** - Called once per path
4. **config.equivalenceAt()** - Called when nodes differ
5. **diffObject()** - Called for each object node
6. **diffArray()** - Called for each array node

## CORRECTNESS GUARANTEES

### Path Determinism

- **Object fields:** Sorted alphabetically via TreeSet
- **Array elements:** Stable pairing (by index or identity)
- **Identity keys:** Sorted via TreeSet before comparison
- **JSON Pointer escaping:** Consistent per RFC 6901

### Equivalence Precedence

Rules evaluated in strict order:
1. Exact path match
2. Pattern match (first matching pattern)
3. Prefix match (longest matching prefix)
4. Type match (requires type hint)
5. Fallback

First match wins; subsequent rules not evaluated.

## EXAMPLE EXECUTION

### Simple Object Diff

**Input:**
```
Left:  {name: "Alice", age: 30}
Right: {name: "Alice", age: 31}

Config: DiffConfig.builder().build()  // Default config
```

**Execution Trace:**
```
1. compare(left, right, config)
   → ctx = new Context(config, [], {})

2. walk("/", left, right, ctx)
   → shouldSkip? NO (not ignored, not visited, not identical, not equivalent)
   → hasLeafNode? NO (both are objects)
   → bothAreContainers? YES
   → compareContainers → diffObject

3. diffObject("/", left, right, ctx)
   → fieldNames = ["age", "name"]  // TreeSet sorted
   → Compare "age":
     → walk("/age", 30, 31, ctx)
       → shouldSkip? NO
       → hasLeafNode? YES (both are value nodes)
       → ctx.add(CHANGED at /age: 30 → 31)
   → Compare "name":
     → walk("/name", "Alice", "Alice", ctx)
       → shouldSkip? YES (areEqual returns true)

4. RETURN ctx.diffs
   → [DiffEntry(path="/age", kind=CHANGED, old=30, new=31)]
```

### Array Diff with Identity Matching

**Input:**
```
Left:  {items: [{id: "A", qty: 1}, {id: "B", qty: 2}]}
Right: {items: [{id: "B", qty: 3}, {id: "A", qty: 1}]}

Config:
  DiffConfig.builder()
    .list("/items", ListRule.id("id"))
    .build()
```

**Execution Trace:**
```
1. walk("/", left, right, ctx)
   → diffObject("/", left, right, ctx)
   → Compare "items":

2. walk("/items", leftArr, rightArr, ctx)
   → diffArray("/items", leftArr, rightArr, ctx)
   → rule = ListRule.id("id")  // Found
   → pairById()

3. pairById("/items", leftArr, rightArr, ctx, rule)
   → Build indices:
     leftIndex = {"A": {id:"A",qty:1}, "B": {id:"B",qty:2}}
     rightIndex = {"B": {id:"B",qty:3}, "A": {id:"A",qty:1}}
   → allKeys = ["A", "B"]  // TreeSet sorted

4. Compare element "A":
   → walk("/items/{A}", {id:"A",qty:1}, {id:"A",qty:1}, ctx)
     → areEqual? YES → skip (no diff)

5. Compare element "B":
   → walk("/items/{B}", {id:"B",qty:2}, {id:"B",qty:3}, ctx)
     → diffObject → Compare "qty":
       → ctx.add(CHANGED at /items/{B}/qty: 2 → 3)

6. RETURN ctx.diffs
   → [DiffEntry(path="/items/{B}/qty", kind=CHANGED, old=2, new=3)]
```

**Key insight:** Elements matched by identity key "id", so reordering didn't produce false diffs.

## RELATED DOCUMENTATION

- [ARCHITECTURE.md](ARCHITECTURE.md) - Architecture and design decisions
- [MAPPING.md](MAPPING.md) - Configuration DSL specification
- [OPERATIONS.md](OPERATIONS.md) - API reference and production operations
- [README.md](../README.md) - Project overview and quick start
