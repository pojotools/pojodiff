package io.github.pojotools.pojodiff.core.api;

import com.fasterxml.jackson.databind.JsonNode;

public record DiffEntry(String path, DiffKind kind, JsonNode oldValue, JsonNode newValue) {}
