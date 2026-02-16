package io.github.cyfko.filterql.spring.processor;

import io.github.cyfko.projection.Method;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for extracting annotations from methods, handling annotation
 * mirrors and values.
 * <p>
 * This class provides mechanisms to:
 * <ul>
 * <li>Extract annotations from specific methods or classes</li>
 * <li>Validate method signatures for annotation lookup</li>
 * <li>Format annotation values into string representations</li>
 * <li>Manage required imports for generated code</li>
 * </ul>
 * </p>
 *
 * @author cyfko
 * @since 1.0
 */
public class AnnotationExtractor {
    private final String defaultMethodName;

    private Types typeUtils;
    private Messager messager;

    // Track imports needed for generated classes
    private final Set<String> requiredImports = new LinkedHashSet<>();

    /**
     * Constructs a new AnnotationExtractor.
     *
     * @param defaultMethodName the default method name to look for if none is
     *                          specified
     * @throws NullPointerException     if defaultMethodName is null
     * @throws IllegalArgumentException if defaultMethodName is blank
     */
    public AnnotationExtractor(String defaultMethodName) {
        Objects.requireNonNull(defaultMethodName, "defaultMethodName cannot be null");
        if (defaultMethodName.isBlank()) {
            throw new IllegalArgumentException("defaultMethodName cannot be blank");
        }
        this.defaultMethodName = defaultMethodName;
    }

    /**
     * Extract annotations from the template method based on {@link Method}
     * configuration.
     *
     * @param processingEnv  the annotation processing environment
     * @param exposedElement the element annotated with @Exposure
     * @param methodRef      the @Method annotation reference
     * @return an {@link ExtractedAnnotation} containing the found annotations and
     *         required imports
     */
    public ExtractedAnnotation extractAnnotations(
            ProcessingEnvironment processingEnv,
            TypeElement exposedElement,
            Method methodRef) {
        this.messager = processingEnv.getMessager();
        this.typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        // Determine target class
        TypeElement targetClass;
        if (isClassSpecified(methodRef)) {
            // External class specified
            String className = getClassName(methodRef);
            if (className == null) {
                targetClass = exposedElement;
            } else {
                targetClass = elementUtils.getTypeElement(className);
            }

            if (targetClass == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot find class: " + className,
                        exposedElement);
                return new ExtractedAnnotation() {
                    @Override
                    public List<AnnotationInfo> getAnnotations() {
                        return List.of();
                    }

                    @Override
                    public Set<String> getRequiredImports() {
                        return Set.of();
                    }
                };
            }
        } else {
            // Use the entity class itself
            targetClass = exposedElement;
        }

        // Determine method name
        String methodName = methodRef.value().isEmpty()
                ? defaultMethodName
                : methodRef.value();

        // Extract annotations from the method
        List<AnnotationInfo> annotations = extractAnnotationsFromMethod(
                targetClass,
                methodName,
                exposedElement);

        // If no method found and using convention, provide helpful message
        if (annotations.isEmpty() && methodRef.value().isEmpty() && !isClassSpecified(methodRef)) {
            messager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "No '" + defaultMethodName + "()' method found. " +
                            "Generating endpoint without custom annotations. " +
                            "To customize, add: private static void " + defaultMethodName + "() {}",
                    exposedElement);
        }

        return new ExtractedAnnotation() {
            @Override
            public List<AnnotationInfo> getAnnotations() {
                return annotations;
            }

            @Override
            public Set<String> getRequiredImports() {
                return requiredImports;
            }
        };
    }

    /**
     * Extract annotations from a specific method in a class.
     *
     * @param classElement   the class containing the method
     * @param methodName     the name of the method to inspect
     * @param contextElement the element triggering this extraction (for error
     *                       reporting)
     * @return a list of {@link AnnotationInfo} found on the method
     */
    private List<AnnotationInfo> extractAnnotationsFromMethod(
            TypeElement classElement,
            String methodName,
            TypeElement contextElement) {
        List<AnnotationInfo> annotations = new ArrayList<>();

        // Find the method
        ExecutableElement targetMethod = null;
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                    enclosedElement.getSimpleName().toString().equals(methodName)) {
                targetMethod = (ExecutableElement) enclosedElement;
                break;
            }
        }

        if (targetMethod == null) {
            // Default method not found ! It is not an error !
            return annotations;
        }

        // Validate method signature
        if (!validateMethodSignature(targetMethod, methodName)) {
            return annotations;
        }

        // Extract all annotations from the method
        for (AnnotationMirror mirror : targetMethod.getAnnotationMirrors()) {
            AnnotationInfo annotationInfo = buildAnnotationInfo(mirror);
            annotations.add(annotationInfo);

            // Record import
            requiredImports.add(annotationInfo.qualifiedName);
        }

        return annotations;
    }

    /**
     * Validate that the method has the correct signature:
     * <ul>
     * <li>void return type</li>
     * <li>no parameters</li>
     * </ul>
     *
     * @param method     the method to validate
     * @param methodName the name of the method (for error messages)
     * @return true if valid, false otherwise (errors are reported to messager)
     */
    private boolean validateMethodSignature(ExecutableElement method, String methodName) {
        boolean isValid = true;

        // Must return void
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Method '" + methodName + "' must return void. " +
                            "Expected signature: private static void " + methodName + "()",
                    method);
            isValid = false;
        }

        // Must take no parameters
        if (!method.getParameters().isEmpty()) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Method '" + methodName + "' must take no parameters. " +
                            "Expected signature: private static void " + methodName + "()",
                    method);
            isValid = false;
        }

        return isValid;
    }

    /**
     * Build annotation information from an {@link AnnotationMirror}.
     *
     * @param mirror the annotation mirror to process
     * @return an {@link AnnotationInfo} record containing the annotation details
     */
    private AnnotationInfo buildAnnotationInfo(AnnotationMirror mirror) {
        DeclaredType annotationType = mirror.getAnnotationType();
        TypeElement annotationElement = (TypeElement) annotationType.asElement();

        String qualifiedName = annotationElement.getQualifiedName().toString();
        String simpleName = annotationElement.getSimpleName().toString();

        // Build the annotation string with its attributes
        String annotationString = buildAnnotationString(mirror, simpleName);

        return new AnnotationInfo(qualifiedName, simpleName, annotationString);
    }

    /**
     * Build the complete string representation of an annotation including its
     * attributes.
     *
     * @param mirror     the annotation mirror
     * @param simpleName the simple name of the annotation class
     * @return the formatted annotation string (e.g.,
     *         {@code @MyAnnotation(value = "foo")})
     */
    private String buildAnnotationString(AnnotationMirror mirror, String simpleName) {
        StringBuilder sb = new StringBuilder("@").append(simpleName);

        Map<? extends ExecutableElement, ? extends AnnotationValue> values = mirror.getElementValues();

        if (!values.isEmpty()) {
            sb.append("(");

            boolean isFirst = true;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {

                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;

                String attrName = entry.getKey().getSimpleName().toString();

                // Omit "value =" if it's the only attribute
                if (values.size() == 1 && "value".equals(attrName)) {
                    sb.append(formatAnnotationValue(entry.getValue()));
                } else {
                    sb.append(attrName).append(" = ");
                    sb.append(formatAnnotationValue(entry.getValue()));
                }
            }

            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Format an annotation value (handles strings, arrays, nested annotations,
     * enums, etc.)
     */
    /**
     * Format an annotation value into its Java source code representation.
     * <p>
     * Handles:
     * <ul>
     * <li>Strings (escaped)</li>
     * <li>Characters</li>
     * <li>Classes (adding imports)</li>
     * <li>Enums</li>
     * <li>Arrays</li>
     * <li>Nested annotations</li>
     * </ul>
     *
     * @param value the annotation value to format
     * @return the string representation of the value
     */
    private String formatAnnotationValue(AnnotationValue value) {
        Object val = value.getValue();

        if (val instanceof String) {
            return "\"" + escapeString((String) val) + "\"";
        } else if (val instanceof Character) {
            return "'" + val + "'";
        } else if (val instanceof Class<?> clazz) {
            requiredImports.add(clazz.getCanonicalName());
            return clazz.getSimpleName() + ".class";
        } else if (val instanceof TypeMirror typeMirror) {
            TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
            if (typeElement != null) {
                requiredImports.add(typeElement.getQualifiedName().toString());
                return typeElement.getSimpleName() + ".class";
            }
            return val.toString();
        } else if (val instanceof List) {
            @SuppressWarnings("unchecked")
            List<? extends AnnotationValue> list = (List<? extends AnnotationValue>) val;

            if (list.isEmpty()) {
                return "{}";
            }

            if (list.size() == 1) {
                return formatAnnotationValue(list.get(0));
            }

            return "{" + list.stream()
                    .map(this::formatAnnotationValue)
                    .collect(Collectors.joining(", ")) + "}";
        } else if (val instanceof AnnotationMirror nestedMirror) {
            // Nested annotation
            TypeElement nestedElement = (TypeElement) nestedMirror.getAnnotationType().asElement();
            requiredImports.add(nestedElement.getQualifiedName().toString());
            return buildAnnotationString(nestedMirror, nestedElement.getSimpleName().toString());
        } else if (val instanceof VariableElement varElement) {
            // Enum constant
            TypeElement enumElement = (TypeElement) varElement.getEnclosingElement();
            requiredImports.add(enumElement.getQualifiedName().toString());
            return enumElement.getSimpleName() + "." + varElement.getSimpleName();
        }

        return val.toString();
    }

    /**
     * Check if a class is specified in the {@link Method} annotation.
     *
     * @param methodRef the Method annotation instance
     * @return true if a specific class is provided, false if using default
     *         (void.class)
     */
    private boolean isClassSpecified(Method methodRef) {
        try {
            Class<?> type = methodRef.type();
            return type != void.class;
        } catch (Exception e) {
            // In annotation processing, accessing Class<?> can throw MirroredTypeException
            // We need to use a different approach
            return true; // If exception thrown, a type was specified
        }
    }

    /**
     * Get the fully qualified class name from the {@link Method} annotation using
     * mirrors.
     * <p>
     * Necessary because accessing Class objects directly in annotation processing
     * throws {@link javax.lang.model.type.MirroredTypeException}.
     *
     * @param methodRef the Method annotation instance
     * @return the fully qualified class name, or null if resolution fails
     */
    private String getClassName(Method methodRef) {
        try {
            // This will throw MirroredTypeException
            methodRef.type();
            return null;
        } catch (javax.lang.model.type.MirroredTypeException e) {
            TypeMirror typeMirror = e.getTypeMirror();
            TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
            return typeElement == null ? null : typeElement.getQualifiedName().toString();
        }
    }

    /**
     * Escape special characters in strings for Java source code.
     *
     * @param str the input string
     * @return the escaped string
     */
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Classes ====================

    /**
     * Holds information about an extracted annotation.
     *
     * @param qualifiedName    the fully qualified name of the annotation
     * @param simpleName       the simple name of the annotation
     * @param annotationString the full string representation of the annotation
     *                         usage
     */
    public record AnnotationInfo(String qualifiedName, String simpleName, String annotationString) {
    }

    /**
     * Result of the annotation extraction process.
     */
    public interface ExtractedAnnotation {
        List<AnnotationInfo> getAnnotations();

        Set<String> getRequiredImports();
    }
}
