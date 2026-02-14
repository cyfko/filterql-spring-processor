package io.github.cyfko.filterql.spring.processor.generator;

import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.processor.util.Logger;
import io.github.cyfko.filterql.spring.processor.util.StringUtils;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.jpametamodel.processor.AnnotationProcessorUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

import static io.github.cyfko.filterql.spring.processor.util.ExposureUtils.toEnumRef;
import static io.github.cyfko.filterql.spring.processor.util.ExposureUtils.validatePipeMethod;
import static io.github.cyfko.filterql.spring.processor.util.ExposureUtils.validateHandlerMethod;

/**
 * Generates the source code for the FilterQL REST search controller based on entity and DTO metadata.
 * <p>
 * Used by the FilterQL annotation processor to produce controller classes exposing search endpoints
 * for each filtered entity, including DTO mapping, request validation, and repository integration.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Invoked during annotation processing for each {@link Exposure} entity</li>
 *   <li>Produces Java source code for REST controllers via templating</li>
 *   <li>Handles endpoint generation, import management, and DTO mapping logic</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>Custom templates via {@link TemplateEngine}</li>
 *   <li>Override for advanced endpoint or mapping logic</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class FilterControllerGenerator {
    private static final String DEFAULT_ANNOTATION_LOOKUP_METHOD_NAME = "searchEndpoint";

    private final TemplateEngine templateEngine;

    // On utilise Set pour éviter les doublons
    private final StringBuilder searchEndpoints = new StringBuilder();
    private final Set<String> annotationsImports = new LinkedHashSet<>();

    /**
     * Constructs a generator using the provided template engine.
     *
     * @param templateEngine engine for loading and processing templates
     */
    public FilterControllerGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Registers a new search endpoint for the given entity and DTO mapping.
     *
     * @param processingEnv annotation processing environment
     * @param projectionClass projection class element
     */
    public void register(ProcessingEnvironment processingEnv, TypeElement projectionClass) {
        Exposure exposure = projectionClass.getAnnotation(Exposure.class);
        if (exposure == null) return;

        // --- Nom exposé sécurisé
        String projectionSimpleName = projectionClass.getSimpleName().toString();
        String exposedName = toExposedName(exposure, projectionSimpleName);
        String basePath = toBasePath(exposure);
        String methodName = exposure.endpointName().isBlank() ? "search" + projectionSimpleName :  exposure.endpointName();
        String fqEnumName = toEnumRef(projectionClass);

        StringBuilder reqTransformation = new StringBuilder();
        StringBuilder handler = new StringBuilder();
        StringBuilder endpointReturn = new StringBuilder();

        AnnotationProcessorUtils.processExplicitFields(projectionClass,
                Exposure.class.getCanonicalName(),
                props -> {
                    if (props.containsKey("pipes")) {
                        //noinspection unchecked
                        List<Map<String, Object>> pipesProp = (List<Map<String, Object>>) props.get("pipes");
                        generateRequestTransformation(pipesProp, reqTransformation, projectionClass, processingEnv);
                    }

                    if (props.containsKey("handler")) {
                        //noinspection unchecked
                        Map<String, Object> handlerProp = (Map<String, Object>) props.get("handler");
                        generateHandlerExpression(handlerProp, handler, projectionClass, endpointReturn, processingEnv);
                    } else {
                        handler.append("searchService.search(").append(fqEnumName).append(".class, req)");
                        Exposure.Strategy strategy = projectionClass.getAnnotation(Exposure.class).strategy();
                        setEndpointReturnType(projectionClass, strategy, endpointReturn, "Object");
                        if (strategy == Exposure.Strategy.LIST){
                            handler.append(".data()");
                        }
                    }
                }, null
        );

        try {
            String searchTemplate = templateEngine.loadTemplate("search-endpoint.java.tpl");

            Map<String, Object> searchContext = new HashMap<>();
            searchContext.put("basePath", basePath);
            searchContext.put("exposedName", exposedName);
            searchContext.put("endpointReturn", endpointReturn.toString());
            searchContext.put("reqTransformation", reqTransformation.toString());
            searchContext.put("handler", handler.toString());
            searchContext.put("methodName", methodName);
            searchContext.put("fqEnumName", fqEnumName);

            // generated code snippet
            String searchSnippet = templateEngine.process(searchTemplate, searchContext);
            if (!searchEndpoints.toString().contains(searchSnippet)) {
                searchEndpoints.append(searchSnippet);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load search-endpoint template", e);
        }

    }

    private static void setEndpointReturnType(TypeElement projectionClass,
                                              Exposure.Strategy strategy,
                                              StringBuilder endpointReturn,
                                              String customFallback) {
        switch (strategy) {
            case PROJECTED -> endpointReturn.append("PaginatedData<Map<String, Object>>");
            case PAGINATED -> endpointReturn.append("PaginatedData<" + projectionClass.toString() + ">");
            case LIST -> endpointReturn.append("List<" + projectionClass.toString() + ">");
            case CUSTOM -> endpointReturn.append(customFallback);
        }
    }

    private void generateHandlerExpression(Map<String, Object> handlerProp,
                                           StringBuilder sb,
                                           TypeElement projectionClass,
                                           StringBuilder endpointReturnBuilder,
                                           ProcessingEnvironment processingEnv) {
        try {
            if (!handlerProp.containsKey("value")) {
                throw new IllegalArgumentException("method name required for explicit handler");
            }

            String methodName = (String) handlerProp.get("value");
            String className = (String) handlerProp.get("type");
            TypeElement classElement = projectionClass;
            if (className == null) {
                className = projectionClass.toString();
            }
            classElement = processingEnv.getElementUtils().getTypeElement(className);

            Exposure.Strategy strategy = projectionClass.getAnnotation(Exposure.class).strategy();
            final String errorMessage = String.format("""
                    Expected method signature of handler not found. Searching for (%s) on class = %s. Strategy = %s
                    """,
                    methodName, className, strategy
            );
            ExecutableElement targetMethod = validateHandlerMethod(classElement, methodName, strategy, projectionClass, processingEnv)
                    .orElseThrow(() -> new IllegalArgumentException(errorMessage));

            setEndpointReturnType(projectionClass, strategy, endpointReturnBuilder, targetMethod.getReturnType().toString());

            boolean isStaticMethod = targetMethod.getModifiers().contains(Modifier.STATIC);
            if (isStaticMethod) {
                sb.append(className).append(".").append(methodName).append("(req)");
            } else {
                sb.append("instanceResolver.resolve(").append(className).append(".class, null).")
                        .append(methodName).append("(req)");
            }
        } catch (Exception e) {
            Logger logger = new Logger(processingEnv);
            logger.error(e.getMessage(), projectionClass);
        }
    }

    private void generateRequestTransformation(List<Map<String, Object>> pipes,
                                               StringBuilder sb,
                                               TypeElement projectionClass,
                                               ProcessingEnvironment processingEnv
                                               ) {
        try {
            // Parcourir la liste des pipes et les appliquer dans l'ordre décrit
            sb.append("// Applying pipes...\n");
            for (int i = 0; i < pipes.size(); i++) {
                Map<String, Object> pipe = pipes.get(i);

                if (!pipe.containsKey("value")) {
                    throw new IllegalArgumentException("method name required for pipes[" + i + "]");
                }

                String methodName = (String) pipe.get("value");
                String className = (String) pipe.get("type");
                TypeElement classElement = projectionClass;
                if (className == null) {
                    className = projectionClass.toString();
                }

                classElement = processingEnv.getElementUtils().getTypeElement(className);
                final String errorMessage = String.format("""
                        Expected method signature of pipes[%d] not found. Searching for (%s) on class = %s
                        """,
                        i, methodName, className
                );
                ExecutableElement targetMethod = validatePipeMethod(classElement, methodName, toEnumRef(projectionClass), processingEnv)
                        .orElseThrow(() -> new IllegalArgumentException(errorMessage));

                boolean isStaticMethod = targetMethod.getModifiers().contains(Modifier.STATIC);

                sb.append("        req = ");
                if (isStaticMethod) {
                    sb.append(className).append(".");
                } else {
                    sb.append("instanceResolver.resolve(").append(className).append(".class, null).");
                }
                sb.append(methodName).append("(req);\n");
            }
        } catch (Exception e) {
            Logger logger = new Logger(processingEnv);
            logger.error(e.getMessage(), projectionClass);
        }
    }

    /**
     * Generates the source code for the search controller.
     *
     * @return Java source code for the controller
     * @throws IOException if template loading or processing fails
     */
    public String generate() throws IOException {
        String template = templateEngine.loadTemplate("search-controller.java.tpl");
        Map<String, Object> context = new HashMap<>();
        context.put("annotationsImports", String.join("\n", annotationsImports));
        context.put("searchEndpoints", searchEndpoints.toString());
        return templateEngine.process(template, context);
    }

    /**
     * Computes the exposed REST resource name for the given entity, based on the specified {@link Exposure} annotation.
     * <p>
     * This method retrieves the {@code value} configured in the embedded {@link Exposure} annotation.
     * If the value is not set or blank, it falls back to converting the entity simple name from camelCase to kebab-case
     * following standard naming conventions.
     * </p>
     *
     * @param exposure the {@link Exposure} exposure configuration
     * @param entitySimpleName the simple class name of the entity (typically without package)
     * @return the exposed REST resource name in kebab-case
     */
    public static String toExposedName(Exposure exposure, String entitySimpleName) {
        return (exposure.value() != null && !exposure.value().isBlank()) ?
                exposure.value().trim() :
                StringUtils.camelToKebabCase(StringUtils.toCamelCase(entitySimpleName));
    }

    /**
     * Computes the base URI path prefix for REST endpoints of the given filtered entity, based on the annotation configuration.
     * <p>
     * Returns the {@code basePath} specified in the embedded {@link Exposure} annotation,
     * trimming whitespace or returning an empty string if unspecified.
     * </p>
     *
     * @param exposure the {@link Exposure} endpoint exposure configuration
     * @return the base URI path prefix for REST endpoints, or empty string if none is configured
     */
    public static String toBasePath(Exposure exposure) {
        return exposure.basePath() != null ? exposure.basePath().trim() : "";
    }
}
