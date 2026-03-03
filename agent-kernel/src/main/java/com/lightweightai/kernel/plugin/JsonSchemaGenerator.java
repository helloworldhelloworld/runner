package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates JSON Schema from Java classes using reflection and Jackson annotations.
 * Supports @JsonProperty and @JsonPropertyDescription annotations.
 */
public class JsonSchemaGenerator {

    /**
     * Custom annotation for property descriptions (similar to Jackson's but more portable)
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface JsonPropertyDescription {
        String value();
    }

    /**
     * Generate JSON Schema from a Java class
     */
    public static JsonSchema generate(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        return generateSchema(type, new HashSet<>());
    }

    private static JsonSchema generateSchema(Class<?> type, Set<Class<?>> visited) {
        // Prevent infinite recursion for circular references
        if (visited.contains(type)) {
            return JsonSchema.object();
        }
        visited.add(type);

        // Handle primitive and simple types
        String jsonType = mapJavaTypeToJsonType(type);
        if (!jsonType.equals("object")) {
            return new JsonSchema(Collections.singletonMap("type", jsonType));
        }

        // Handle object types with fields
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // Process all fields
        for (Field field : getAllFields(type)) {
            field.setAccessible(true);

            // Check if field has @JsonProperty
            JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
            String propertyName = field.getName();
            if (jsonProp != null && !jsonProp.value().isEmpty()) {
                propertyName = jsonProp.value();
            }

            // Generate property schema
            Map<String, Object> propSchema = generatePropertySchema(field, visited);
            properties.put(propertyName, propSchema);

            // Check if required
            if (jsonProp != null && jsonProp.required()) {
                required.add(propertyName);
            }
        }

        if (!properties.isEmpty()) {
            schema.put("properties", properties);
        }
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return new JsonSchema(schema);
    }

    private static Map<String, Object> generatePropertySchema(Field field, Set<Class<?>> visited) {
        Map<String, Object> propSchema = new HashMap<>();

        // Get type
        Class<?> fieldType = field.getType();
        Type genericType = field.getGenericType();

        // Handle collections
        if (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            propSchema.put("type", "array");

            // Try to get element type
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    Class<?> elementType = (Class<?>) typeArgs[0];
                    JsonSchema itemSchema = generateSchema(elementType, visited);
                    propSchema.put("items", itemSchema.toMap());
                }
            } else if (fieldType.isArray()) {
                Class<?> componentType = fieldType.getComponentType();
                JsonSchema itemSchema = generateSchema(componentType, visited);
                propSchema.put("items", itemSchema.toMap());
            }
        } else {
            // Handle simple types
            String jsonType = mapJavaTypeToJsonType(fieldType);
            propSchema.put("type", jsonType);

            // For nested objects, include the full schema
            if (jsonType.equals("object")) {
                JsonSchema nestedSchema = generateSchema(fieldType, new HashSet<>(visited));
                propSchema.putAll(nestedSchema.toMap());
            }
        }

        // Add description if available
        JsonPropertyDescription desc = field.getAnnotation(JsonPropertyDescription.class);
        if (desc != null) {
            propSchema.put("description", desc.value());
        }

        return propSchema;
    }

    private static String mapJavaTypeToJsonType(Class<?> javaType) {
        // String types
        if (javaType == String.class || javaType == Character.class || javaType == char.class) {
            return "string";
        }

        // Integer types
        if (javaType == Integer.class || javaType == int.class ||
            javaType == Long.class || javaType == long.class ||
            javaType == Short.class || javaType == short.class ||
            javaType == Byte.class || javaType == byte.class) {
            return "integer";
        }

        // Number types
        if (javaType == Double.class || javaType == double.class ||
            javaType == Float.class || javaType == float.class) {
            return "number";
        }

        // Boolean types
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        }

        // Array/Collection types
        if (javaType.isArray() || Collection.class.isAssignableFrom(javaType)) {
            return "array";
        }

        // Default to object
        return "object";
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;

        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * Generate schema with custom description
     */
    public static JsonSchema generate(Class<?> type, String description) {
        JsonSchema schema = generate(type);
        Map<String, Object> schemaMap = new HashMap<>(schema.toMap());
        if (description != null) {
            schemaMap.put("description", description);
        }
        return new JsonSchema(schemaMap);
    }
}
