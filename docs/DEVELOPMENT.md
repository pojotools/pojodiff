# Development Guide

This document covers development environment setup, build instructions, code quality tools, and testing for pojodiff contributors.

## Quick Start

```bash
# Clone repository
git clone https://github.com/pojotools/pojodiff.git
cd pojodiff

# Run full build with all checks
mvn clean verify
```

## Prerequisites

- **Java 21** or later
- **Maven 3.9+**
- **Git 2.x**

## Project Structure

```
pojodiff/
├── pojodiff-core/          # Core diff engine and configuration
├── pojodiff-jackson/       # Jackson integration and type inference
├── pojodiff-spi/           # Extension interfaces (TypeHints)
├── pojodiff-examples/      # Usage examples and integration tests
├── pojodiff-benchmarks/    # JMH performance benchmarks
└── pojodiff-coverage/      # Aggregated test coverage reports
```

## Building from Source

### Full Build

```bash
# Clean build with all tests and static analysis
mvn clean verify

# Skip tests (for quick compilation)
mvn clean install -DskipTests

# Run only unit tests
mvn clean test
```

### Module-Specific Builds

```bash
# Build only core module
mvn clean verify -pl pojodiff-core

# Build with dependencies
mvn clean verify -pl pojodiff-jackson -am
```

## Code Quality

This project maintains high code quality standards through comprehensive static analysis.

### Static Analysis Tools

#### Checkstyle

Enforces **Clean Code principles**:
- Method complexity ≤15
- Method length ≤50 lines
- Parameters ≤6 per method (target ≤4)
- Line length ≤120 characters
- Mandatory braces, switch defaults
- Proper import organization
- Consistent naming conventions

```bash
# Run checkstyle only
mvn checkstyle:check

# View checkstyle report
open target/site/checkstyle.html
```

#### SpotBugs

Identifies potential bugs and security vulnerabilities through static analysis.

```bash
# Run SpotBugs
mvn spotbugs:check

# View SpotBugs report
open target/spotbugsXml.xml
```

#### PMD

Advanced source code analyzer for finding common programming flaws.

```bash
# Run PMD
mvn pmd:check

# View PMD report
open target/site/pmd.html
```

#### Spotless (Code Formatting)

The project uses **Spotless** with **Google Java Format**.

```bash
# Apply formatting to all files
mvn spotless:apply

# Check formatting without applying
mvn spotless:check
```

**Important:** Always run `mvn spotless:apply` before committing.

### Code Coverage (JaCoCo)

Tracks test coverage across modules with **60% minimum threshold**.

```bash
# Generate coverage report
mvn clean verify

# View aggregated coverage report
open pojodiff-coverage/target/site/jacoco-aggregate/index.html
```

**Coverage Architecture:**
- Cross-module coverage aggregation via dedicated `pojodiff-coverage` module
- Tests in `pojodiff-examples` exercise code in `pojodiff-core` and `pojodiff-jackson`
- HTML reports available at `pojodiff-coverage/target/site/jacoco-aggregate/index.html`

### Running All Quality Checks

```bash
# Full quality gate (recommended before PR)
mvn clean verify spotless:check
```

All static analysis tools run automatically during `mvn verify`.

## Testing

### Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=DiffEngineTest

# Run specific test method
mvn test -Dtest=DiffEngineTest#testObjectDiff
```

### Integration Tests

Located in `pojodiff-examples` module:

```bash
# Run integration tests only
mvn verify -pl pojodiff-examples
```

### Performance Benchmarks (JMH)

Located in `pojodiff-benchmarks` module:

```bash
# Run all benchmarks
cd pojodiff-benchmarks
mvn clean verify
java -jar target/benchmarks.jar

# Run specific benchmark
java -jar target/benchmarks.jar DiffEngineBenchmark
```

## IDE Setup

### IntelliJ IDEA

1. **Import Project:**
   - File → Open → Select `pom.xml`
   - Use Maven import settings
   - JDK: Java 21+

2. **Code Style:**
   - Install **Google Java Format** plugin
   - Settings → Editor → Code Style → Java → Scheme: Google Style

3. **Static Analysis:**
   - Install **CheckStyle-IDEA** plugin
   - Configure with `checkstyle.xml` from project root

4. **Run Configurations:**
   - Maven: `clean verify` (recommended for full checks)
   - Maven: `clean test` (for quick test runs)

### Eclipse

1. **Import Project:**
   - File → Import → Maven → Existing Maven Projects
   - Select `pojodiff` directory

2. **Code Style:**
   - Install **google-java-format** Eclipse plugin
   - Preferences → Java → Code Style → Formatter: Google Style

3. **Static Analysis:**
   - Install **Checkstyle** Eclipse plugin
   - Configure with project `checkstyle.xml`

### VS Code

1. **Extensions:**
   - Install **Extension Pack for Java**
   - Install **Checkstyle for Java**

2. **Settings:**
   ```json
   {
     "java.configuration.maven.userSettings": "~/.m2/settings.xml",
     "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
     "java.checkstyle.configuration": "${workspaceFolder}/checkstyle.xml"
   }
   ```

## Debugging

### Enable Debug Logging

Add to test resources or application properties:

```properties
logging.level.io.github.pojotools.pojodiff=DEBUG
```

### Debugging Diff Issues

```java
// Convert to JsonNode first to inspect structure
ObjectMapper mapper = new ObjectMapper();
JsonNode left = mapper.valueToTree(oldObject);
JsonNode right = mapper.valueToTree(newObject);

// Use default config
DiffConfig config = DiffConfig.builder().build();

// Get diffs
List<DiffEntry> diffs = DiffEngine.compare(left, right, config);

// Pretty print for debugging
diffs.forEach(diff ->
    System.out.printf("%s at %s: %s -> %s%n",
        diff.kind(), diff.path(), diff.oldValue(), diff.newValue()));
```

### Attach Debugger to Tests

```bash
# Run tests with debug enabled (port 5005)
mvn test -Dmaven.surefire.debug
```

Then attach your IDE debugger to port 5005.

## Continuous Integration

The project uses GitHub Actions for CI/CD:

- **Build:** `mvn clean verify` on every push
- **Code Quality:** Checkstyle, SpotBugs, PMD, Spotless
- **Coverage:** JaCoCo reports uploaded to GitHub Pages
- **Benchmarks:** JMH benchmarks run on release branches

See `.github/workflows/` for CI configuration.

## Common Development Tasks

### Add New Feature

1. Create feature branch: `git checkout -b feature/my-feature`
2. Write tests first (TDD approach)
3. Implement feature
4. Run quality checks: `mvn clean verify`
5. Format code: `mvn spotless:apply`
6. Commit with clear message
7. Push and create PR

### Fix Bug

1. Create bug branch: `git checkout -b fix/bug-description`
2. Add failing test demonstrating bug
3. Fix bug until test passes
4. Verify: `mvn clean verify`
5. Format: `mvn spotless:apply`
6. Commit and PR

### Update Dependencies

```bash
# Check for dependency updates
mvn versions:display-dependency-updates

# Update specific dependency
mvn versions:use-latest-versions -Dincludes=com.fasterxml.jackson.core:*

# Verify build after update
mvn clean verify
```

### Add New Module

1. Create module directory
2. Add `pom.xml` with parent reference
3. Add module to root `pom.xml` `<modules>` section
4. Follow existing module structure
5. Update `pojodiff-coverage` aggregation if needed

## Troubleshooting

### Build Fails with "Could not resolve dependencies"

```bash
# Clear local Maven repository cache
rm -rf ~/.m2/repository/io/github/pojotools

# Rebuild from clean state
mvn clean install
```

### Checkstyle Violations

```bash
# View detailed violations
mvn checkstyle:check

# Auto-fix formatting issues
mvn spotless:apply
```

### JaCoCo Coverage Below Threshold

```bash
# View coverage report
open pojodiff-coverage/target/site/jacoco-aggregate/index.html

# Identify untested code paths
# Add tests to increase coverage
```

### OutOfMemoryError During Build

```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"
mvn clean verify
```

## Performance Profiling

### JMH Benchmarks

```bash
cd pojodiff-benchmarks
mvn clean verify
java -jar target/benchmarks.jar -prof gc
```

### JFR (Java Flight Recorder)

```bash
# Run with JFR enabled
mvn test -Dmaven.surefire.argLine="-XX:StartFlightRecording=duration=60s,filename=myrecording.jfr"

# Analyze recording
jfr print myrecording.jfr
```

## Release Process

For maintainers, see **[RELEASE.md](RELEASE.md)** for complete release workflow.

## Related Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design and architecture
- [PSEUDOCODE.md](PSEUDOCODE.md) - Algorithm flow and component design
- [RELEASE.md](RELEASE.md) - Release process for maintainers
- [README.md](../README.md) - Project overview

## Getting Help

- **Issues:** Open a GitHub issue for bugs or feature requests
- **Discussions:** Use GitHub Discussions for questions
- **Documentation:** Check docs/ directory for detailed guides
