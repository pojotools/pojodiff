# Changelog

All notable changes to pojodiff will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0-SNAPSHOT] - Current Development

### Added
- `equivalentGlob()` method for glob-based equivalence pattern matching
- `numericWithin(double)` equivalence method for numeric tolerance comparison
- `offsetDateTimeWithin(Duration)` equivalence method for OffsetDateTime tolerance
- Comprehensive test suite for new equivalence methods
- `changedEntry()` helper method in tests for cleaner assertions

### Changed
- Updated CI workflow to match flat2pojo (Maven caching, coverage extraction, GitHub Pages deployment)
- Improved test assertions to use strict `containsExactly()` pattern throughout
- Enhanced documentation with correct behavior descriptions for path normalization

### Fixed
- Corrected `normalizePathForTypeHint()` pseudocode to match actual implementation
- Fixed documentation to accurately reflect prefix matching (not wildcard) for `equivalentUnder()`
- Removed invalid `tokenAuth` parameter from Maven Central publishing configuration
- Updated all documentation to list only existing `Equivalences` methods

## [0.1.0] - 2025-01-XX

### Features
- Jackson-based JSON tree comparison engine
- Identity-based list element matching
- Configurable equivalence predicates with precedence (exact > pattern > prefix > type > fallback)
- Path ignoring (exact, prefix, pattern, glob)
- Type hints for type-scoped equivalences
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
