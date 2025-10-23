package io.github.pojotools.pojodiff.core.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.pojodiff.core.api.DiffEntry;
import io.github.pojotools.pojodiff.core.api.PojoDiff;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for PojoDiffCore implementation.
 */
public class PojoDiffCoreTest {

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void compareJsonNodesWithNoDifferences() {
    // ARRANGE
    PojoDiff differ = new PojoDiffCore();
    ObjectNode left = JSON.objectNode().put("name", "Alice").put("age", 30);
    ObjectNode right = JSON.objectNode().put("name", "Alice").put("age", 30);
    DiffConfig config = DiffConfig.builder().build();

    // ACT
    List<DiffEntry> diffs = differ.compare(left, right, config);

    // ASSERT
    assertThat(diffs).isEmpty();
  }

  @Test
  void compareJsonNodesWithDifferences() {
    // ARRANGE
    PojoDiff differ = new PojoDiffCore();
    ObjectNode left = JSON.objectNode().put("name", "Alice").put("age", 30);
    ObjectNode right = JSON.objectNode().put("name", "Bob").put("age", 25);
    DiffConfig config = DiffConfig.builder().build();

    // ACT
    List<DiffEntry> diffs = differ.compare(left, right, config);

    // ASSERT
    assertThat(diffs).hasSize(2);
  }

  @Test
  void comparePojosWithNoDifferences() {
    // ARRANGE
    PojoDiff differ = new PojoDiffCore();
    Person left = new Person("Alice", 30);
    Person right = new Person("Alice", 30);
    DiffConfig config = DiffConfig.builder().build();

    // ACT
    List<DiffEntry> diffs = differ.compare(left, right, MAPPER, config);

    // ASSERT
    assertThat(diffs).isEmpty();
  }

  @Test
  void comparePojosWithDifferences() {
    // ARRANGE
    PojoDiff differ = new PojoDiffCore();
    Person left = new Person("Alice", 30);
    Person right = new Person("Bob", 25);
    DiffConfig config = DiffConfig.builder().build();

    // ACT
    List<DiffEntry> diffs = differ.compare(left, right, MAPPER, config);

    // ASSERT
    assertThat(diffs).hasSize(2);
    assertThat(diffs).anyMatch(d -> d.path().equals("/name"));
    assertThat(diffs).anyMatch(d -> d.path().equals("/age"));
  }

  @Test
  void compareComplexPojos() {
    // ARRANGE
    PojoDiff differ = new PojoDiffCore();
    Address leftAddr = new Address("123 Main St", "New York");
    Address rightAddr = new Address("456 Oak Ave", "New York");
    PersonWithAddress left = new PersonWithAddress("Alice", leftAddr);
    PersonWithAddress right = new PersonWithAddress("Alice", rightAddr);
    DiffConfig config = DiffConfig.builder().build();

    // ACT
    List<DiffEntry> diffs = differ.compare(left, right, MAPPER, config);

    // ASSERT
    assertThat(diffs).hasSize(1);
    assertThat(diffs).anyMatch(d -> d.path().equals("/address/street"));
  }

  // Test POJOs
  private static class Person {
    public String name;
    public int age;

    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }
  }

  private static class Address {
    public String street;
    public String city;

    public Address(String street, String city) {
      this.street = street;
      this.city = city;
    }
  }

  private static class PersonWithAddress {
    public String name;
    public Address address;

    public PersonWithAddress(String name, Address address) {
      this.name = name;
      this.address = address;
    }
  }
}
