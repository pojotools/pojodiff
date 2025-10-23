package io.github.pojotools.pojodiff.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.pojotools.pojodiff.core.util.GlobPatterns;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Tests for GlobPatterns glob-to-regex conversion functionality. */
public class GlobPatternsTest {

  // Single-star tests

  @Test
  void singleStarMatchesSegment() {
    Pattern pattern = GlobPatterns.globToRegex("/items/*/name");

    assertTrue(pattern.matcher("/items/123/name").matches());
    assertTrue(pattern.matcher("/items/abc/name").matches());
    assertTrue(pattern.matcher("/items/xyz/name").matches());
    assertFalse(pattern.matcher("/items/123/456/name").matches());
    assertFalse(pattern.matcher("/items/name").matches());
  }

  @Test
  void singleStarMatchesEmptyString() {
    // [^/]* matches zero or more non-slash chars, including empty string
    Pattern pattern = GlobPatterns.globToRegex("/users/*/profile");

    assertTrue(pattern.matcher("/users/john/profile").matches());
    assertTrue(pattern.matcher("/users//profile").matches()); // matches empty between slashes
    assertFalse(pattern.matcher("/users/john/smith/profile").matches());
  }

  @Test
  void multipleSingleStarsInPath() {
    Pattern pattern = GlobPatterns.globToRegex("/*/data/*");

    assertTrue(pattern.matcher("/users/data/123").matches());
    assertTrue(pattern.matcher("/items/data/abc").matches());
    assertFalse(pattern.matcher("/users/nested/data/123").matches());
  }

  // Double star tests

  @Test
  void doubleStarMatchesAnythingIncludingSlashes() {
    Pattern pattern = GlobPatterns.globToRegex("/items/**/name");

    assertTrue(pattern.matcher("/items//name").matches()); // .* matches empty
    assertTrue(pattern.matcher("/items/123/name").matches());
    assertTrue(pattern.matcher("/items/a/b/c/name").matches());
    assertTrue(pattern.matcher("/items/deep/nested/path/name").matches());
  }

  @Test
  void doubleStarAtBeginning() {
    Pattern pattern = GlobPatterns.globToRegex("**/metadata");

    // .* at the start needs something before it to anchor
    assertTrue(pattern.matcher("/metadata").matches());
    assertTrue(pattern.matcher("config/metadata").matches());
    assertTrue(pattern.matcher("a/b/c/metadata").matches());
  }

  @Test
  void doubleStarAtEnd() {
    Pattern pattern = GlobPatterns.globToRegex("/config/**");

    assertTrue(pattern.matcher("/config/").matches());
    assertTrue(pattern.matcher("/config/settings").matches());
    assertTrue(pattern.matcher("/config/nested/deep/value").matches());
  }

  @Test
  void multipleDoubleStars() {
    Pattern pattern = GlobPatterns.globToRegex("/**/data/**/value");

    assertTrue(pattern.matcher("/users/data/123/value").matches());
    assertTrue(pattern.matcher("/a/b/c/data/x/y/z/value").matches());
    assertTrue(pattern.matcher("/items/data//value").matches()); // .* can match empty
  }

  // Question mark tests

  @Test
  void questionMarkMatchesSingleCharacter() {
    Pattern pattern = GlobPatterns.globToRegex("/items/?/name");

    assertTrue(pattern.matcher("/items/a/name").matches());
    assertTrue(pattern.matcher("/items/1/name").matches());
    assertFalse(pattern.matcher("/items/ab/name").matches());
    assertFalse(pattern.matcher("/items//name").matches());
  }

  @Test
  void questionMarkDoesNotMatchSlash() {
    Pattern pattern = GlobPatterns.globToRegex("/user?name");

    assertTrue(pattern.matcher("/user1name").matches());
    assertTrue(pattern.matcher("/userAname").matches());
    assertFalse(pattern.matcher("/user/name").matches());
  }

  @Test
  void multipleQuestionMarks() {
    Pattern pattern = GlobPatterns.globToRegex("/id-???");

    assertTrue(pattern.matcher("/id-123").matches());
    assertTrue(pattern.matcher("/id-abc").matches());
    assertFalse(pattern.matcher("/id-12").matches());
    assertFalse(pattern.matcher("/id-1234").matches());
  }

  // Mixed wildcards tests

  @Test
  void mixedStarAndQuestionMark() {
    Pattern pattern = GlobPatterns.globToRegex("/items/*/id-??");

    assertTrue(pattern.matcher("/items/users/id-12").matches());
    assertTrue(pattern.matcher("/items/data/id-ab").matches());
    assertFalse(pattern.matcher("/items/users/id-123").matches());
  }

  @Test
  void mixedSingleAndDoubleStar() {
    Pattern pattern = GlobPatterns.globToRegex("/*/config/**/settings");

    assertTrue(pattern.matcher("/app/config//settings").matches()); // .* matches empty
    assertTrue(pattern.matcher("/app/config/nested/settings").matches());
    assertTrue(pattern.matcher("/data/config/a/b/c/settings").matches());
    assertFalse(pattern.matcher("/app/data/config/settings").matches());
  }

  // Regex metacharacter escaping tests

  @Test
  void escapesRegexMetacharacters() {
    Pattern pattern = GlobPatterns.globToRegex("/items/(test)");

    assertTrue(pattern.matcher("/items/(test)").matches());
    assertFalse(pattern.matcher("/items/test").matches());
  }

  @Test
  void escapesDots() {
    Pattern pattern = GlobPatterns.globToRegex("/file.json");

    assertTrue(pattern.matcher("/file.json").matches());
    assertFalse(pattern.matcher("/fileXjson").matches());
  }

  @Test
  void escapesBrackets() {
    Pattern pattern = GlobPatterns.globToRegex("/array[0]");

    assertTrue(pattern.matcher("/array[0]").matches());
    assertFalse(pattern.matcher("/array0").matches());
  }

  @Test
  void escapesCurlyBraces() {
    Pattern pattern = GlobPatterns.globToRegex("/items/{id}");

    assertTrue(pattern.matcher("/items/{id}").matches());
    assertFalse(pattern.matcher("/items/123").matches());
  }

  @Test
  void escapesMultipleMetacharacters() {
    Pattern pattern = GlobPatterns.globToRegex("/data.{test}[0]");

    assertTrue(pattern.matcher("/data.{test}[0]").matches());
    assertFalse(pattern.matcher("/dataX{test}[0]").matches());
  }

  @Test
  void escapesBackslash() {
    Pattern pattern = GlobPatterns.globToRegex("/path\\separator");

    assertTrue(pattern.matcher("/path\\separator").matches());
  }

  // Edge cases and special patterns

  @Test
  void emptyGlob() {
    Pattern pattern = GlobPatterns.globToRegex("");

    assertTrue(pattern.matcher("").matches());
    assertFalse(pattern.matcher("anything").matches());
  }

  @Test
  void onlyWildcards() {
    Pattern pattern1 = GlobPatterns.globToRegex("*");
    assertTrue(pattern1.matcher("anything").matches());
    assertTrue(pattern1.matcher("").matches()); // [^/]* matches empty
    assertFalse(pattern1.matcher("any/thing").matches());

    Pattern pattern2 = GlobPatterns.globToRegex("**");
    assertTrue(pattern2.matcher("anything").matches());
    assertTrue(pattern2.matcher("any/thing").matches());
    assertTrue(pattern2.matcher("").matches()); // .* matches empty
  }

  @Test
  void literalPathWithNoWildcards() {
    Pattern pattern = GlobPatterns.globToRegex("/users/profile/email");

    assertTrue(pattern.matcher("/users/profile/email").matches());
    assertFalse(pattern.matcher("/users/profile/name").matches());
    assertFalse(pattern.matcher("/users/123/profile/email").matches());
  }

  @Test
  void consecutiveStarsNotAtBoundary() {
    Pattern pattern = GlobPatterns.globToRegex("/a**/b");

    assertTrue(pattern.matcher("/a/b").matches());
    assertTrue(pattern.matcher("/a/x/y/b").matches());
    assertTrue(pattern.matcher("/abc/b").matches());
  }

  // Real-world JSON Pointer path patterns

  @Test
  void matchesMetadataPattern() {
    Pattern pattern = GlobPatterns.globToRegex("/**/metadata/**");

    assertTrue(pattern.matcher("/config/metadata/version").matches());
    assertTrue(pattern.matcher("/data/metadata/source").matches());
    assertTrue(pattern.matcher("/a/b/c/metadata/x/y/z").matches());
    assertFalse(pattern.matcher("/config/data").matches());
  }

  @Test
  void matchesArrayIndexPattern() {
    Pattern pattern = GlobPatterns.globToRegex("/items/*/name");

    assertTrue(pattern.matcher("/items/0/name").matches());
    assertTrue(pattern.matcher("/items/123/name").matches());
    assertTrue(pattern.matcher("/items/{id}/name").matches());
  }

  @Test
  void matchesNestedArrayPattern() {
    Pattern pattern = GlobPatterns.globToRegex("/matrix/*/*/value");

    assertTrue(pattern.matcher("/matrix/0/1/value").matches());
    assertTrue(pattern.matcher("/matrix/row/col/value").matches());
    assertFalse(pattern.matcher("/matrix/0/value").matches());
  }

  @Test
  void matchesTemporaryFieldsPattern() {
    Pattern pattern = GlobPatterns.globToRegex("/temp*");

    assertTrue(pattern.matcher("/temp").matches());
    assertTrue(pattern.matcher("/temp1").matches());
    assertTrue(pattern.matcher("/temporary").matches());
    assertFalse(pattern.matcher("/data/temp").matches());
  }

  @Test
  void matchesIdFieldsAtAnyDepth() {
    Pattern pattern = GlobPatterns.globToRegex("/**/*Id");

    assertTrue(pattern.matcher("//userId").matches()); // /** at start can match empty
    assertTrue(pattern.matcher("/data/accountId").matches());
    assertTrue(pattern.matcher("/nested/deep/customerId").matches());
    assertFalse(pattern.matcher("/data/identity").matches());
  }

  @Test
  void matchesExactPathSegments() {
    Pattern pattern = GlobPatterns.globToRegex("/users/*/settings/*");

    assertTrue(pattern.matcher("/users/john/settings/theme").matches());
    assertTrue(pattern.matcher("/users/123/settings/locale").matches());
    assertFalse(pattern.matcher("/users/john/profile/settings/theme").matches());
  }
}
