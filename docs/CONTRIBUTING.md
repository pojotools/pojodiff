# Contributing to pojodiff

Thanks for your interest in contributing! Here's how to get started.

## Development Setup

See **[DEVELOPMENT.md](DEVELOPMENT.md)** for complete development environment setup, build instructions, and code quality tools.

Quick start:

```bash
# Clone and build
git clone https://github.com/pojotools/pojodiff.git
cd pojodiff
mvn clean verify
```

## Project Structure

- `pojodiff-core/` - Core diff engine and configuration
- `pojodiff-jackson/` - Jackson integration and type inference
- `pojodiff-spi/` - Extension interfaces (TypeHints, NodeTreeFactory)
- `pojodiff-examples/` - Usage examples and integration tests
- `pojodiff-benchmarks/` - JMH performance benchmarks

## Making Changes

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/your-feature`
3. **Write** tests for your changes
4. **Ensure** all tests pass: `mvn clean verify`
5. **Format** code: `mvn spotless:apply`
6. **Submit** a pull request

## Code Standards

- All new code must have tests
- Maintain 80%+ test coverage
- Follow existing code style (Google Java Format)
- Update documentation for public APIs
- No breaking changes without discussion

### Clean Code Guidelines

This project follows clean code principles:

- **≤4 parameters** per method - Use context objects for complex parameter lists
- **≤1 indent level** - Use guard clauses and early returns
- **~4-6 lines per method** - Small, focused functions
- **Positive conditionals** - `if (isValid())` not `if (!isInvalid())`
- **Single Responsibility** - One reason to change per class
- **Dependency Injection** - Constructor injection over `new`
- **Intention-revealing names** - Clear, descriptive names

## Architecture Understanding

Before making significant changes, review:

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Design decisions and system architecture
- **[PSEUDOCODE.md](PSEUDOCODE.md)** - Algorithm flow and component interactions
- **[MAPPING.md](MAPPING.md)** - Configuration DSL specification

## Release Process

For maintainers, see **[RELEASE.md](RELEASE.md)** for release process and versioning guidelines.

## Questions?

Open an issue for discussion before starting large changes.

## Related Documentation

- [DEVELOPMENT.md](DEVELOPMENT.md) - Development environment and build setup
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design and architecture
- [RELEASE.md](RELEASE.md) - Release process for maintainers
- [README.md](../README.md) - Project overview
