package io.github.cyfko.filterql.spring.processor.generator;

import io.github.cyfko.filterql.spring.processor.FieldMetadata;

import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;

/**
 * Generates the source code for a type-safe PropertyRef enum for a
 * FilterQL-annotated entity.
 * <p>
 * Used by the FilterQL annotation processor to create enums representing
 * filterable properties,
 * including their types and supported operators, based on entity and field
 * metadata.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 * <li>Invoked during annotation processing for each
 * {@link io.github.cyfko.projection.Projection} entity</li>
 * <li>Produces Java source code for the PropertyRef enum via templating</li>
 * <li>Handles i18n, type imports, and operator mapping</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <ul>
 * <li>Custom templates via {@link TemplateEngine}</li>
 * <li>Override for advanced enum generation logic</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class PropertyRefEnumGenerator {
    private final TemplateEngine templateEngine;

    /**
     * Constructs a generator using the provided template engine.
     *
     * @param templateEngine engine for loading and processing templates
     */
    public PropertyRefEnumGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Generates the source code for the PropertyRef enum for the given entity
     * (contract testing version).
     * <p>
     * This overload accepts i18nPrefix directly instead of extracting it from the
     * annotation,
     * allowing unit testing without runtime annotation access.
     * </p>
     *
     * @param packageName          target package for the enum
     * @param projectionSimpleName simple name of the projection
     * @param enumName             name of the enum to generate
     * @param fields               list of filterable field metadata
     * @return Java source code for the PropertyRef enum
     * @throws RuntimeException if template loading or processing fails
     */
    public String generate(String packageName,
            String projectionSimpleName,
            String enumName,
            List<FieldMetadata> fields) {
        try {
            String template = templateEngine.loadTemplate("property-ref-enum.java.tpl");
            Map<String, Object> context = new HashMap<>();
            final String projectionFqcn = packageName + "." + projectionSimpleName;

            context.put("packageName", packageName);
            context.put("enumName", enumName);
            context.put("constants", generateConstants(fields));
            context.put("enumToFieldTypeSwitch", generateEnumToTypeSwitch(projectionFqcn, fields));
            context.put("enumToOperatorsSwitch", generateEnumToOperatorSwitch(fields));
            context.put("entityClass", generateEntityClass(projectionFqcn));

            return templateEngine.process(template, context);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate enum", e);
        }
    }

    /**
     * Generates a comma-separated list of enum constants for the PropertyRef.
     *
     * @param fields list of field metadata
     * @return comma-separated string of enum constants
     */
    private String generateConstants(List<FieldMetadata> fields) {
        return fields.stream()
                .map(FieldMetadata::referredAs)
                .collect(Collectors.joining(", "));
    }

    /**
     * Generates a switch expression mapping each enum constant to its Java type
     * class.
     *
     * @param projectionFqcn fully qualified class name of the projection
     * @param fields         list of field metadata
     * @return Java code for the switch expression
     */
    private String generateEnumToTypeSwitch(String projectionFqcn, List<FieldMetadata> fields) {
        final StringBuilder sb = new StringBuilder()
                .append("        var pm = ProjectionRegistry.getMetadataFor(").append(projectionFqcn)
                .append(".class);\n")
                .append("        return switch(this){\n");

        for (FieldMetadata field : fields) {
            sb.append("            case ").append(field.referredAs()).append(" -> ");
            if (field.isVirtual()) {
                sb.append("Object.class;\n");
            } else {
                sb.append("pm.getDirectMapping(\"").append(field.name()).append("\"")
                        .append(", true).get().dtoFieldType();\n");
            }
        }

        return sb.append("        };").toString();
    }

    /**
     * Generates a switch expression mapping each enum constant to its allowed
     * operators.
     *
     * @param fields list of field metadata
     * @return Java code for the switch expression
     */
    private String generateEnumToOperatorSwitch(List<FieldMetadata> fields) {
        final StringBuilder sb = new StringBuilder("        return switch(this){\n");

        for (FieldMetadata field : fields) {
            sb.append("            case ")
                    .append(field.referredAs())
                    .append(" -> ")
                    .append(generateConstant(field)).append(";\n");
        }

        return sb.append("        };").toString();
    }

    /**
     * Generates a set of valid operators for a specific field.
     *
     * @param field field metadata
     * @return Java code creating a Set of Op enums
     */
    private String generateConstant(FieldMetadata field) {
        String operators = Arrays.stream(field.operators())
                .map(op -> "Op." + op.name())
                .collect(Collectors.joining(", "));

        return String.format("Set.of(%s)", operators);
    }

    /**
     * Generates the code to retrieve the entity class from the registry.
     *
     * @param projectionFqcn fully qualified class name of the projection
     * @return Java code expression for the entity class
     */
    private String generateEntityClass(String projectionFqcn) {
        return "ProjectionRegistry.getMetadataFor(" + projectionFqcn + ".class).entityClass()";
    }
}
