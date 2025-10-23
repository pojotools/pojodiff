package io.github.pojotools.pojodiff.examples;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import io.github.pojotools.pojodiff.core.config.ListRule;
import io.github.pojotools.pojodiff.core.engine.DiffEngine;
import io.github.pojotools.pojodiff.core.equivalence.Equivalences;
import java.util.List;
import org.junit.jupiter.api.Test;

public class QuickstartTest {
  record Item(String id, int v) {}

  record Doc(String name, java.util.List<Item> items) {}

  @Test
  void quickstart() {
    ObjectMapper mapper = new ObjectMapper();
    Doc left = new Doc("Alice", java.util.List.of(new Item("1", 1)));
    Doc right = new Doc("ALICE", java.util.List.of(new Item("1", 2)));

    DiffConfig cfg =
        DiffConfig.builder()
            .list("/items", ListRule.id("id"))
            .equivalentAt("/name", Equivalences.caseInsensitive())
            .build();

    List<DiffEntry> diffs =
        DiffEngine.compare(mapper.valueToTree(left), mapper.valueToTree(right), cfg);
    assertEquals(1, diffs.size());
  }
}
