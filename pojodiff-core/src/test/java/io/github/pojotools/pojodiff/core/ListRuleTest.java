package io.github.pojotools.pojodiff.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.pojotools.pojodiff.core.config.ListRule;
import org.junit.jupiter.api.Test;

/** Tests for ListRule configuration and identifier path detection. */
public class ListRuleTest {

  @Test
  void idDetectsFieldName() {
    ListRule rule = ListRule.id("id");

    assertFalse(rule.isNone());
    assertEquals("id", rule.identifierPath());
    assertFalse(rule.isPointer(), "Should detect as field, not pointer");
  }

  @Test
  void idDetectsJsonPointer() {
    ListRule rule = ListRule.id("/nested/id");

    assertFalse(rule.isNone());
    assertEquals("/nested/id", rule.identifierPath());
    assertTrue(rule.isPointer(), "Should detect as JSON Pointer");
  }

  @Test
  void idDetectsSimpleJsonPointer() {
    ListRule rule = ListRule.id("/id");

    assertEquals("/id", rule.identifierPath());
    assertTrue(rule.isPointer());
  }

  @Test
  void idDetectsComplexFieldName() {
    ListRule rule = ListRule.id("user_id");

    assertEquals("user_id", rule.identifierPath());
    assertFalse(rule.isPointer());
  }

  @Test
  void idRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> ListRule.id(null));
  }

  @Test
  void idRejectsEmptyString() {
    assertThrows(IllegalArgumentException.class, () -> ListRule.id(""));
  }

  @Test
  void idHandlesUuidField() {
    ListRule rule = ListRule.id("uuid");

    assertEquals("uuid", rule.identifierPath());
    assertFalse(rule.isPointer());
  }

  @Test
  void idHandlesDeepPointer() {
    ListRule rule = ListRule.id("/data/nested/deep/id");

    assertEquals("/data/nested/deep/id", rule.identifierPath());
    assertTrue(rule.isPointer());
  }

  @Test
  void noneRuleCreatesEmptyIdentifier() {
    ListRule rule = ListRule.none();

    assertTrue(rule.isNone());
    assertEquals("", rule.identifierPath());
    assertFalse(rule.isPointer());
  }

  @Test
  void pointerMethodReturnsCorrectValue() {
    ListRule fieldRule = ListRule.id("id");
    ListRule pointerRule = ListRule.id("/nested/id");

    assertEquals(false, fieldRule.pointer());
    assertEquals(true, pointerRule.pointer());
  }
}
