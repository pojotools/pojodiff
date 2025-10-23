package io.github.pojotools.pojodiff.spi;

import com.fasterxml.jackson.databind.JsonNode;

/** Creates JSON trees from POJOs for diffing. */
@FunctionalInterface
public interface NodeTreeFactory {
  JsonNode toTree(Object value);
}
