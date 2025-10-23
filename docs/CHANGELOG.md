# Changelog

All notable changes to pojodiff will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive documentation suite
  - ARCHITECTURE.md - Architecture and design decisions
  - PSEUDOCODE.md - Algorithm flow and component design
  - DEVELOPMENT.md - Development environment setup guide
  - OPERATIONS.md - Production operations and API usage
  - CONTRIBUTING.md - Contribution guidelines
  - RELEASE.md - Release process documentation
  - COVERAGE-BADGE-SETUP.md - Coverage badge configuration
  - CHANGELOG.md - Version history (this file)
- Documentation map in README.md

### Changed
- Expanded MAPPING.md with comprehensive configuration examples
- Enhanced ARCHITECTURE.md with detailed component descriptions
- Enhanced PSEUDOCODE.md with complete algorithm flow and diagrams

## [0.1.0-SNAPSHOT] - Current Development

### Features
- Jackson-based JSON tree comparison engine
- Identity-based list element matching
- Configurable equivalence predicates with precedence (exact > pattern > prefix > type > fallback)
- Path ignoring (exact, prefix, pattern, glob)
- Type hints for type-scoped equivalences
- Cycle detection strategies (NONE, IDENTITY)
- JSON Pointer paths for precise change location
- Built-in equivalences:
  - Numeric tolerance (`numericWithin`)
  - Case-insensitive string comparison
  - Punctuation-insensitive comparison
  - Time-based tolerance (Instant, OffsetDateTime, ZonedDateTime)
- Clean code architecture with SOLID principles
- Comprehensive test coverage
- JMH performance benchmarks

### Technical Details
- Java 21+ required
- Jackson 2.17+ integration
- Multi-module Maven project
- Comprehensive static analysis (Checkstyle, PMD, SpotBugs, Spotless)
- Thread-safe, immutable configuration
- Performance-optimized with identity-based operations

## Release Notes

For detailed release notes and migration guides, see individual version entries above.

For the release process, see [RELEASE.md](RELEASE.md).

## Related Documentation

- [RELEASE.md](RELEASE.md) - Release process and versioning
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [README.md](../README.md) - Project overview
