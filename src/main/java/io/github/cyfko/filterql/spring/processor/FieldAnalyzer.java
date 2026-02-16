package io.github.cyfko.filterql.spring.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.BiConsumer;

import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.processor.util.ExposureUtils;
import io.github.cyfko.filterql.spring.support.SupportedType;
import io.github.cyfko.jpametamodel.processor.AnnotationProcessorUtils;
import io.github.cyfko.jpametamodel.processor.StringUtils;
import io.github.cyfko.projection.Computed;
import io.github.cyfko.filterql.spring.support.DefaultOperatorStrategy;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.projection.Projection;

/**
 * Analyzes entity classes annotated for FilterQL and extracts metadata for all
 * filterable fields.
 * <p>
 * Used by the FilterQL annotation processor to discover regular and virtual
 * fields, determine their types,
 * supported operators, and generate metadata for code generation and runtime
 * validation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 * <li>Invoked during annotation processing to build {@link FieldMetadata} for
 * each entity</li>
 * <li>Handles both persisted JPA fields and virtual fields defined via
 * {@link ExposedAs}</li>
 * <li>Supports custom generation strategies and exclusion rules</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 * <li>Uses {@link DefaultOperatorStrategy} for operator assignment</li>
 * <li>Handles annotation mirror extraction for safe type resolution</li>
 * <li>Extensible for custom field analysis logic</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class FieldAnalyzer {

    private final ProcessingEnvironment processingEnv;
    private final DefaultOperatorStrategy operatorStrategy;
    List<FieldMetadata> fields;
    Set<String> observedField;

    /**
     * Constructs a new FieldAnalyzer.
     *
     * @param processingEnv the annotation processing environment
     */
    public FieldAnalyzer(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.operatorStrategy = new DefaultOperatorStrategy();
    }

    /**
     * analyze a projection entity to find all filterable fields (regular and
     * virtual).
     *
     * @param projectionClass the class annotated with @Projection
     * @return a list of field metadata
     */
    public List<FieldMetadata> analyzeProjection(TypeElement projectionClass) {
        fields = new ArrayList<>();
        observedField = new HashSet<>();

        for (Element element : projectionClass.getEnclosedElements()) {
            Set<Modifier> modifiers = element.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE))
                continue; // Skip static and private element
            if (element.getAnnotation(Computed.class) != null)
                continue; // Skip computed fields

            ExecutableElement methodElement = (ExecutableElement) element;
            String refName = extractRefName(methodElement);
            SupportedType javaType = extractJavaType(methodElement);
            Op[] operators = extractOperators(methodElement, javaType);

            fields.add(FieldMetadata.regularField(refName, StringUtils.toJavaNamingAwareFieldName(methodElement),
                    operators));
        }

        // Search for virtual fields on providers
        AnnotationProcessorUtils.processExplicitFields(
                projectionClass,
                Projection.class.getName(),
                params -> {
                    // record the target entity
                    Elements elementUtils = processingEnv.getElementUtils();
                    TypeElement entity = elementUtils.getTypeElement((String) params.get("from"));

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> providers = (List<Map<String, Object>>) params.get("providers");
                    if (providers == null)
                        return;

                    for (Map<String, Object> provider : providers) {
                        TypeElement providerElement = elementUtils.getTypeElement((String) provider.get("value"));
                        String beanName = (String) provider.get("name");
                        useProvider(providerElement, beanName, entity);
                    }
                },
                null);

        return fields;
    }

    /**
     * Process a provider class to find virtual fields.
     *
     * @param providerElement the provider class element
     * @param beanName        the Spring bean name of the provider
     * @param entity          the target entity class
     */
    private void useProvider(TypeElement providerElement, String beanName, TypeElement entity) {
        for (Element providerMethod : providerElement.getEnclosedElements()) {
            if (providerMethod.getKind() != ElementKind.METHOD)
                continue;

            FieldMetadata fieldMetadata = scanVirtualFieldsInClass(
                    providerElement,
                    beanName,
                    (ExecutableElement) providerMethod,
                    entity);

            if (fieldMetadata != null) {
                fields.add(fieldMetadata);
            }
        }
    }

    /**
     * Scans a specific method in a provider class to see if it exposes a virtual
     * field.
     *
     * @param resolverClass  the provider class
     * @param beanName       the provider bean name
     * @param resolverMethod the method to check
     * @param entity         the target entity
     * @return metadata if a valid virtual field is found, null otherwise
     */
    private FieldMetadata scanVirtualFieldsInClass(TypeElement resolverClass,
            String beanName,
            ExecutableElement resolverMethod,
            TypeElement entity) {
        ExposedAs annotation = resolverMethod.getAnnotation(ExposedAs.class);
        if (annotation == null)
            return null;
        if (!resolverMethod.getModifiers().contains(Modifier.PUBLIC))
            return null;

        // Only consider return type PredicateResolver<E>
        Types typeUtils = processingEnv.getTypeUtils();
        TypeMirror predicateResolver = ExposureUtils.parameterizedWith(PredicateResolver.class.getName(), entity,
                processingEnv);
        if (!typeUtils.isSameType(resolverMethod.getReturnType(), predicateResolver)) {
            return null;
        }

        List<? extends VariableElement> params = resolverMethod.getParameters();

        // Validate parameters: must be -> PredicateResolver<E> methodXyz(String op,
        // Object[] args)
        if (!params.isEmpty() && params.size() != 2) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Virtual filter property must have exactly 2 parameters: (String op, Object[] args)",
                    resolverMethod);
            return null;
        }

        String param0Type = params.get(0).asType().toString();
        String param1Type = params.get(1).asType().toString();
        if (!param0Type.equals("java.lang.String") || !param1Type.equals("java.lang.Object[]")) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Virtual filter property must have parameters (String op, Object[] args), found: (" + param0Type
                            + ", " + param1Type + ")",
                    resolverMethod);
            return null;
        }

        final String referenceName = annotation.value();
        if (observedField.contains(referenceName)) {
            return null;
        }

        String methodName = resolverMethod.getSimpleName().toString();
        boolean isStatic = resolverMethod.getModifiers().contains(Modifier.STATIC);
        String resolverClassName = resolverClass.getQualifiedName().toString();

        observedField.add(referenceName);
        return FieldMetadata.virtualField(
                referenceName,
                methodName,
                annotation.operators(),
                resolverClassName,
                beanName,
                isStatic);
    }

    /**
     * Extracts the supported FilterQL type from a method's return type.
     *
     * @param methodElement the method element
     * @return the supported type (string, int, enum, etc.)
     */
    private SupportedType extractJavaType(ExecutableElement methodElement) {
        TypeMirror typeMirror = methodElement.getReturnType();
        return extractSupportedType(typeMirror);
    }

    /**
     * Maps a TypeMirror to a SupportedType.
     * <p>
     * Handles standard types and Enums.
     *
     * @param typeMirror the type to analyze
     * @return the corresponding SupportedType or UNKNOWN
     */
    private SupportedType extractSupportedType(TypeMirror typeMirror) {
        // D'abord essayer la détection par nom
        SupportedType fromName = SupportedType.fromTypeMirror(typeMirror);
        if (fromName != SupportedType.UNKNOWN) {
            return fromName;
        }

        // Ensuite vérifier si c'est une enum
        TypeElement typeElement = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
        if (typeElement != null && typeElement.getKind() == ElementKind.ENUM) {
            return SupportedType.ENUM;
        }

        return SupportedType.UNKNOWN;
    }

    /**
     * Determines the operators available for a field.
     * <p>
     * uses the @ExposedAs annotation if present, otherwise defaults based on the
     * type.
     *
     * @param methodElement the field's accessor method
     * @param supportedType the resolved supported type
     * @return array of allowed operators
     */
    private Op[] extractOperators(ExecutableElement methodElement, SupportedType supportedType) {
        ExposedAs annotation = methodElement.getAnnotation(ExposedAs.class);
        if (annotation != null && annotation.operators().length > 0) {
            return annotation.operators();
        }
        return operatorStrategy.getDefaultOperators(supportedType);
    }

    // Les méthodes utilitaires restent les mêmes
    /**
     * Extracts the reference name for the field (for the front-end).
     * <p>
     * Uses @ExposedAs value if set, otherwise converts the method name to
     * UpperSnakeCase.
     *
     * @param methodElement the method element
     * @return the field reference name
     */
    private String extractRefName(ExecutableElement methodElement) {
        ExposedAs annotation = methodElement.getAnnotation(ExposedAs.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return toUpperSnakeCase(StringUtils.toJavaNamingAwareFieldName(methodElement));
    }

    /**
     * Converts a camelCase string to UPPER_SNAKE_CASE.
     *
     * @param camelCase input string
     * @return converted string
     */
    private String toUpperSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }
}
