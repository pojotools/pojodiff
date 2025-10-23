package io.github.pojotools.pojodiff.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.pojodiff.core.config.DiffConfig;
import java.util.List;

/**
 * High-level facade for comparing POJOs using Jackson serialization.
 *
 * <p><b>Single Responsibility:</b> Provides a convenient API for comparing Java objects
 * by converting them to JSON trees and detecting differences.
 *
 * <p>Example usage:
 * <pre>{@code
 * PojoDiff differ = new PojoDiffCore();
 * ObjectMapper mapper = new ObjectMapper();
 * DiffConfig config = DiffConfig.builder().build();
 *
 * Person left = new Person("Alice", 30);
 * Person right = new Person("Alice", 31);
 *
 * List<DiffEntry> diffs = differ.compare(left, right, mapper, config);
 * }</pre>
 */
public interface PojoDiff {

  /**
   * Compares two JSON trees and detects differences.
   *
   * @param left The left-hand JSON tree to compare
   * @param right The right-hand JSON tree to compare
   * @param config The diff configuration (list rules, ignores, equivalences, etc.)
   * @return A list of differences found between the two JSON trees
   */
  List<DiffEntry> compare(JsonNode left, JsonNode right, DiffConfig config);

  /**
   * Compares two POJOs by converting them to JSON trees and detecting differences.
   *
   * @param left The left-hand object to compare
   * @param right The right-hand object to compare
   * @param mapper The ObjectMapper used for POJO-to-JSON conversion
   * @param config The diff configuration (list rules, ignores, equivalences, etc.)
   * @param <T> The type of objects being compared
   * @return A list of differences found between the two objects
   */
  <T> List<DiffEntry> compare(T left, T right, ObjectMapper mapper, DiffConfig config);
}

