package io.github.pojotools.pojodiff.jackson;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.pojodiff.spi.TypeHints;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Debug test to verify recursion prevention works correctly. */
public class RecursionDebugTest {

  @Test
  void debugScheduleRecursion() {
    ObjectMapper mapper = new ObjectMapper();

    // This should complete without StackOverflowError
    TypeHints hints = JacksonAdapters.inferTypeHints(Schedule.class, mapper);

    assertThat(hints).isNotNull();

    // Print all recorded hints to see what paths are captured
    System.out.println("=== Type Hints for Schedule ===");
    // We can't easily iterate the map, but we can test specific paths

    // Root path
    System.out.println("/ → " + hints.resolve("/"));

    // Direct field
    System.out.println("/name → " + hints.resolve("/name"));

    // Collection field (should be empty - not a leaf)
    System.out.println("/exclusionSchedules → " + hints.resolve("/exclusionSchedules"));

    // What about nested fields if we HAD array indices?
    System.out.println("/exclusionSchedules/0/name → " + hints.resolve("/exclusionSchedules/0/name"));

    assertThat(hints.resolve("/name"))
        .as("Direct field should be present")
        .isPresent()
        .hasValue("java.lang.String");
  }

  static class Schedule {
    private String name;
    private List<Schedule> exclusionSchedules;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Schedule> getExclusionSchedules() {
      return exclusionSchedules;
    }

    public void setExclusionSchedules(List<Schedule> exclusionSchedules) {
      this.exclusionSchedules = exclusionSchedules;
    }
  }
}
