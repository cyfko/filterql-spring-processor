package io.github.cyfko.filterql.spring.processor.generator;

import io.github.cyfko.filterql.spring.processor.FieldMetadata;
import io.github.cyfko.jpametamodel.processor.StringUtils;

import java.util.*;
import java.io.IOException;

/**
 * Generates the filter context configuration source code based on metadata
 * extracted from a filtered entity.
 * <p>
 * This class uses a {@code TemplateEngine} to produce Java source code that
 * defines filter configuration classes,
 * which integrate entity metadata and filtering-related annotations.
 * </p>
 *
 * <p>
 * The generation process composes package declarations, class names, bean names
 * in camelCase,
 * and registers virtual fields requiring special resolver imports and logic.
 * </p>
 *
 * <p>
 * Generated configuration is used at runtime for filter validation and field
 * resolution.
 * </p>
 *
 * <p>
 * <b>Usage:</b>
 * </p>
 * 
 * <pre>{@code
 * FilterContextGenerator generator = new FilterContextGenerator(templateEngine);
 * String generatedSource = generator.generate(
 *         processingEnv,
 *         filteredAnnotation,
 *         "com.example.filters",
 *         "CustomerEntity",
 *         "CustomerPropertyRef",
 *         listOfFieldMetadata);
 * }</pre>
 *
 * <b>Key methods:</b>
 * <ul>
 * <li>{@code generate(...)} - main source generation method</li>
 * field-specific logic</li>
 * </ul>
 */
public class FilterContextGenerator {
    private final TemplateEngine templateEngine;
    private final String instanceTemplateSource;
    private final String configTemplateSource;
    private final List<String> contextCodes = new ArrayList<>();

    /**
     * Constructs a new {@code FilterContextGenerator} using the provided template
     * engine.
     *
     * @param templateEngine the template processing engine to render source code
     *                       templates
     */
    public FilterContextGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        try {
            this.instanceTemplateSource = templateEngine.loadTemplate("filter-context-instance.java.tpl");
            this.configTemplateSource = templateEngine.loadTemplate("filter-context-config.java.tpl");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Registers a projection's configuration generation context.
     *
     * @param packageName    the target package name
     * @param projectionFqcn the fully qualified class name of the projection
     * @param fields         the list of fields to include in the filter context
     * @param entityClass    the fully qualified class name of the target entity
     */
    public void register(String packageName,
            String projectionFqcn,
            List<FieldMetadata> fields,
            String entityClass) {

        boolean hasVirtualFields = fields.stream().anyMatch(FieldMetadata::isVirtual);
        String contextParam = hasVirtualFields ? "InstanceResolver instanceResolver" : "";

        Map<String, Object> context = new HashMap<>();
        context.put("propertyRefEnumName", projectionFqcn + "_");
        context.put("beanName", "contextOf" + StringUtils.getSimpleClassName(projectionFqcn));
        context.put("contextParam", contextParam);
        context.put("switchCases", generateSwitchCases(fields, entityClass));

        final String code = templateEngine.process(instanceTemplateSource, context);
        contextCodes.add(code);
    }

    /**
     * Generates filter context Java source code for a specified filtered entity.
     * 
     * @return generated source code as a String
     */
    public String generate() {
        Map<String, Object> context = new HashMap<>();
        context.put("contextInstances", String.join("\n", contextCodes));
        return templateEngine.process(configTemplateSource, context);
    }

    /**
     * Generates switch-case statements to map field references to JPA paths or
     * resolver calls.
     *
     * @param fields      list of fields to generate cases for
     * @param entityClass fully qualified entity class name
     * @return switch-case code block as a string
     */
    private String generateSwitchCases(List<FieldMetadata> fields, String entityClass) {
        StringBuilder sb = new StringBuilder();
        for (FieldMetadata field : fields) {
            sb.append("                case ").append(field.referredAs()).append(" -> ");
            if (field.isVirtual()) {
                FieldMetadata.VirtualFieldDetails details = field.virtualFieldDetails();

                // PredicateResolver<E> method(String op, Object[] args)
                // Wrap into PredicateResolverMapping using generic type
                if (details.isStatic()) {
                    sb.append("(PredicateResolverMapping<").append(entityClass).append(">) (op, args) -> ")
                            .append(details.resolverClassName()).append(".").append(field.name()).append("(op, args);");
                } else {
                    sb.append("(PredicateResolverMapping<").append(entityClass)
                            .append(">) (op, args) -> (PredicateResolver<").append(entityClass)
                            .append(">) ProjectionUtils.invoke(instanceResolver, ")
                            .append(details.resolverClassName()).append(".class, ");
                    if (details.beanName() != null) {
                        sb.append("\"").append(details.beanName()).append("\", ");
                    } else {
                        sb.append("null, ");
                    }
                    sb.append("\"").append(field.name()).append("\", op, args);");
                }
            } else {
                // Champ régulier : JPA path
                sb.append("\"").append(field.name()).append("\";");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
