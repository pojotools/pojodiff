package io.github.pojotools.pojodiff.spi;

import java.util.Optional;

/** Maps JSON Pointers to implementation-specific type keys (e.g., class names). */
public interface TypeHints {
  Optional<String> resolve(String jsonPointer);
}
