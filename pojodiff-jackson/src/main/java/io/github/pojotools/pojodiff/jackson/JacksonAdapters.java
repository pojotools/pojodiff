package io.github.pojotools.pojodiff.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import io.github.pojotools.pojodiff.core.util.PathUtils;
import io.github.pojotools.pojodiff.spi.TypeHints;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Jackson-specific adapters for PojoDiff integration.
 *
 * <p><b>Single Responsibility:</b> Provides type-hint inference for Jackson-annotated
 * classes using ObjectMapper metadata, with configurable recursion depth limits and
 * cycle detection for self-referential types.
 */
public final class JacksonAdapters {
  private JacksonAdapters() {}

  /**
   * Type-hint inference with default configuration (depth 1 for self-referential types).
   * Stops after one level of recursion to prevent infinite loops.
   */
  public static TypeHints inferTypeHints(Class<?> rootType, ObjectMapper mapper) {
    return inferTypeHints(rootType, mapper, TypeHintInferenceConfig.defaultConfig());
  }

  /**
   * Type-hint inference with custom configuration for recursion depth control.
   *
   * <p>Example configurations:
   * <pre>{@code
   * // Stop at first self-reference (most conservative)
   * TypeHints hints = inferTypeHints(Schedule.class, mapper,
   *     TypeHintInferenceConfig.stopAtFirstReference());
   *
   * // Custom global depth
   * TypeHints hints = inferTypeHints(Schedule.class, mapper,
   *     TypeHintInferenceConfig.builder()
   *         .defaultMaxDepth(2)
   *         .build());
   *
   * // Per-path depth control
   * TypeHints hints = inferTypeHints(Schedule.class, mapper,
   *     TypeHintInferenceConfig.builder()
   *         .defaultMaxDepth(1)
   *         .maxDepthForPrefix("/exclusionSchedules", 3)  // Deeper for this path
   *         .maxDepthForPrefix("/metadata", 0)            // No recursion here
   *         .build());
   * }</pre>
   *
   * @param rootType The root class to infer type hints from
   * @param mapper The ObjectMapper for serialization metadata
   * @param config Configuration controlling recursion depth behavior
   */
  public static TypeHints inferTypeHints(
      Class<?> rootType, ObjectMapper mapper, TypeHintInferenceConfig config) {
    var context = new InferenceContext(mapper, config);
    context.walk("", mapper.constructType(rootType));
    return context.buildTypeHints();
  }

  /**
   * Encapsulates the traversal state for type hint inference.
   * Reduces parameter passing and groups related data.
   */
  private static final class InferenceContext {
    private final ObjectMapper mapper;
    private final TypeHintInferenceConfig config;
    private final Map<String, String> typeHints = new LinkedHashMap<>();
    private final Map<JavaType, Integer> depthTracker = new HashMap<>();

    InferenceContext(ObjectMapper mapper, TypeHintInferenceConfig config) {
      this.mapper = mapper;
      this.config = config;
    }

    TypeHints buildTypeHints() {
      return new MapBackedTypeHints(typeHints);
    }

    void walk(String path, JavaType type) {
      if (type == null) {
        return;
      }

      JavaType unwrapped = unwrapOptionalType(type);
      if (isWrappedType(unwrapped, type)) {
        walk(path, unwrapped);
        return;
      }

      if (isCollectionType(type)) {
        walkCollectionType(path, type);
        return;
      }

      if (exceedsMaxDepth(path, type)) {
        return;
      }

      processWithDepthTracking(path, type);
    }

    private JavaType unwrapOptionalType(JavaType type) {
      Class<?> rawClass = type.getRawClass();

      if (isGenericOptional(rawClass, type)) {
        return type.containedType(0);
      }

      if (isPrimitiveOptional(rawClass)) {
        return unwrapPrimitiveOptional(rawClass);
      }

      return type;
    }

    private boolean isGenericOptional(Class<?> rawClass, JavaType type) {
      return rawClass.equals(Optional.class) && type.containedTypeCount() == 1;
    }

    private boolean isPrimitiveOptional(Class<?> rawClass) {
      return rawClass == java.util.OptionalInt.class
          || rawClass == java.util.OptionalLong.class
          || rawClass == java.util.OptionalDouble.class;
    }

    private JavaType unwrapPrimitiveOptional(Class<?> rawClass) {
      Class<?> wrapperClass = getPrimitiveWrapperClass(rawClass);
      return constructType(wrapperClass);
    }

    private Class<?> getPrimitiveWrapperClass(Class<?> rawClass) {
      if (rawClass == java.util.OptionalInt.class) {
        return Integer.class;
      }
      if (rawClass == java.util.OptionalLong.class) {
        return Long.class;
      }
      return Double.class;
    }

    private JavaType constructType(Class<?> clazz) {
      return mapper.getTypeFactory().constructType(clazz);
    }

    private boolean isWrappedType(JavaType unwrapped, JavaType original) {
      return !unwrapped.equals(original);
    }

    private static boolean isCollectionType(JavaType type) {
      return type.isCollectionLikeType() || type.isArrayType();
    }

    private void walkCollectionType(String path, JavaType type) {
      JavaType elementType = type.getContentType();
      if (elementType != null) {
        // Normalize path: /items/{id}/name -> /items/name
        // This makes type hints structure-based, not instance-based
        walk(path, elementType);
      }
    }

    private boolean exceedsMaxDepth(String path, JavaType type) {
      int currentDepth = depthTracker.getOrDefault(type, 0);
      int maxDepth = config.maxDepthForPath(normalizePath(path));
      return currentDepth > maxDepth;
    }

    private static String normalizePath(String path) {
      return path.isEmpty() ? "/" : path;
    }

    private void processWithDepthTracking(String path, JavaType type) {
      int currentDepth = depthTracker.getOrDefault(type, 0);
      depthTracker.put(type, currentDepth + 1);
      try {
        processType(path, type);
      } finally {
        depthTracker.put(type, currentDepth);
      }
    }

    private void processType(String path, JavaType type) {
      if (isLeaf(type)) {
        recordTypeHint(path, type);
        return;
      }
      walkObjectProperties(path, type);
    }

    private static boolean isLeaf(JavaType type) {
      Class<?> rawClass = type.getRawClass();
      return rawClass.isPrimitive()
          || rawClass.getName().startsWith("java.")
          || rawClass.isEnum();
    }

    private void recordTypeHint(String path, JavaType type) {
      typeHints.put(normalizePath(path), type.getRawClass().getName());
    }

    private void walkObjectProperties(String path, JavaType type) {
      BeanSerializer serializer = createSerializer(type);
      if (serializer == null) {
        return;
      }

      Iterator<PropertyWriter> properties = serializer.properties();
      while (properties.hasNext()) {
        walkProperty(path, properties.next());
      }
    }

    private BeanSerializer createSerializer(JavaType type) {
      try {
        var provider = mapper.getSerializerProviderInstance();
        var serializer = BeanSerializerFactory.instance.createSerializer(provider, type);
        return (serializer instanceof BeanSerializer s) ? s : null;
      } catch (JsonMappingException e) {
        return null; // Type cannot be serialized
      }
    }

    private void walkProperty(String basePath, PropertyWriter writer) {
      if (!(writer instanceof BeanPropertyWriter bpw)) {
        return;
      }

      String propertyName = PathUtils.escape(writer.getName());
      String childPath = PathUtils.child(basePath, propertyName);
      walk(childPath, bpw.getType());
    }
  }

  private record MapBackedTypeHints(Map<String, String> map) implements TypeHints {
    @Override
    public Optional<String> resolve(String jsonPointer) {
      return Optional.ofNullable(map.get(jsonPointer));
    }
  }
}
