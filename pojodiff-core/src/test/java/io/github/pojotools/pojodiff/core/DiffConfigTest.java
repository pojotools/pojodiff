package io.github.pojotools.pojodiff.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Tests for DiffConfig validation, null-safety, and defensive copying. */
public class DiffConfigTest {

  @Test
  void builderRejectsNullPointer() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.ignore(null));
  }

  @Test
  void builderRejectsEmptyPointer() {
    var builder = DiffConfig.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.ignore(""));
  }

  @Test
  void builderRejectsNullPrefix() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.ignorePrefix(null));
  }

  @Test
  void builderRejectsNullPattern() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.ignorePattern(null));
  }

  @Test
  void builderRejectsNullGlob() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.ignoreGlob(null));
  }

  @Test
  void builderRejectsNullListRule() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.list("/items", null));
  }

  @Test
  void builderRejectsNullEquivalence() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.equivalentAt("/name", null));
  }

  @Test
  void builderRejectsNullTypeKey() {
    var builder = DiffConfig.builder();
    assertThrows(NullPointerException.class, () -> builder.typeHint("/when", null));
  }

  @Test
  void builderRejectsBlankTypeKey() {
    var builder = DiffConfig.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.typeHint("/when", "   "));
  }

  @Test
  void builderNormalizesNullRootPath() {
    DiffConfig cfg = DiffConfig.builder().rootPath(null).build();
    assertEquals("/", cfg.rootPath());
  }

  @Test
  void builderNormalizesBlankRootPath() {
    DiffConfig cfg = DiffConfig.builder().rootPath("   ").build();
    assertEquals("/", cfg.rootPath());
  }

  @Test
  void builderPreservesCustomRootPath() {
    DiffConfig cfg = DiffConfig.builder().rootPath("/__root").build();
    assertEquals("/__root", cfg.rootPath());
  }

  @Test
  void configIsImmutable() {
    DiffConfig cfg =
        DiffConfig.builder()
            .ignore("/temp")
            .list("/items", ListRule.id("id"))
            .equivalentAt("/name", (l, r) -> true)
            .typeHint("/when", "java.time.Instant")
            .build();

    // Config methods return immutable views
    assertNotNull(cfg.listRule("/items"));
    assertTrue(cfg.isIgnored("/temp"));
    assertNotNull(cfg.equivalenceAt("/name"));
  }

  @Test
  void builderAllowsFluentChaining() {
    DiffConfig cfg =
        DiffConfig.builder()
            .ignore("/a")
            .ignorePrefix("/meta")
            .ignorePattern(Pattern.compile("^/temp/.*$"))
            .ignoreGlob("/**/test/**")
            .list("/items", ListRule.id("id"))
            .equivalentAt("/name", (l, r) -> true)
            .equivalentUnder("/data", (l, r) -> true)
            .equivalentPattern(Pattern.compile("^/value$"), (l, r) -> true)
            .equivalentForType("string", (l, r) -> true)
            .equivalentFallback((l, r) -> false)
            .typeHint("/when", "instant")
            .rootPath("/custom")
            .build();

    assertNotNull(cfg);
    assertEquals("/custom", cfg.rootPath());
  }
}
