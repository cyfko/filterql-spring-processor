package io.github.cyfko.filterql.spring.processor.util;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.jpametamodel.processor.AnnotationProcessorUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class ExposureUtils {
    /**
     * Validates that a pipe method has the correct signature.
     * <p>
     * A valid pipe method must:
     * </p>
     * <ul>
     *   <li>Be public</li>
     *   <li>Return FilterRequest</li>
     *   <li>Have FilterRequest as first parameter</li>
     *   <li>May have additional parameters for dependency injection</li>
     * </ul>
     *
     * @param typeElement the class containing the pipe method
     * @param methodName the name of the pipe method
     * @param fqcnPropertyRef the FQCN of generated enum implementing {@link io.github.cyfko.filterql.core.api.PropertyReference}
     * @param processingEnv the processing environment
     * @return an Optional containing the ExecutableElement if valid, or empty otherwise
     */
    public static Optional<ExecutableElement> validatePipeMethod(
            TypeElement typeElement,
            String methodName,
            String fqcnPropertyRef,
            ProcessingEnvironment processingEnv) {

        TypeMirror filterRequestType = genericFilterRequestType(fqcnPropertyRef, processingEnv);

        return AnnotationProcessorUtils.findMethodWithSignature(
                typeElement,
                methodName,
                Set.of(Modifier.PUBLIC),
                filterRequestType,  // Must return FilterRequest
                null,
                true,
                processingEnv
        ).filter(method -> {
            // Verify unique parameter is FilterRequest
            List<? extends VariableElement> parameters = method.getParameters();
            if (parameters.size() != 1) {
                return false;
            }

            TypeMirror firstParamType = parameters.get(0).asType();
            Types typeUtils = processingEnv.getTypeUtils();
            return typeUtils.isSameType(firstParamType, filterRequestType);
        });
    }

    /**
     * Validates that a handler method has the correct signature for the given strategy.
     * <p>
     * Each strategy requires specific signatures:
     * </p>
     * <ul>
     *   <li>PAGINATED: Paginated&lt;T&gt; method(FilterRequest)</li>
     *   <li>LIST: List&lt;T&gt; method(FilterRequest)</li>
     *   <li>COUNT: long method(FilterRequest)</li>
     *   <li>SINGLE: Optional&lt;T&gt; method(FilterRequest)</li>
     *   <li>CUSTOM: [Any return type]  method(FilterRequest)</li>
     * </ul>
     *
     * @param typeElement the class containing the handler method
     * @param methodName the name of the handler method
     * @param strategy the exposure strategy
     * @param projection the projection class
     * @param penv the processing environment
     * @return an Optional containing the ExecutableElement if valid, or empty otherwise
     */
    public static Optional<ExecutableElement> validateHandlerMethod(
            TypeElement typeElement,
            String methodName,
            Exposure.Strategy strategy,  // ExposureStrategy name as string
            TypeElement projection,
            ProcessingEnvironment penv) {

        // Find method with public static modifiers
        return AnnotationProcessorUtils.findMethodWithSignature(
                typeElement,
                methodName,
                Set.of(Modifier.PUBLIC),
                null,  // Check return type later based on strategy
                null,  // Check parameters later
                false,
                penv
        ).filter(method -> {
            // Verify first parameter is FilterRequest
            List<? extends VariableElement> parameters = method.getParameters();
            if (parameters.size() != 1) {
                return false;
            }

            Types typeUtils = penv.getTypeUtils();
            String enumRef = toEnumRef(projection);

            if (parameters.size() != 1) {
                return false;
            }

            final var paramType = parameters.get(0).asType();
            if (! typeUtils.isSameType(paramType, genericFilterRequestType(enumRef, penv))){
                return false;
            }

            TypeMirror returnType = method.getReturnType();
            Elements elementUtils = penv.getElementUtils();
            return switch (strategy) {
                case PAGINATED -> typeUtils.isSameType(parameterizedWith(PaginatedData.class.getName(), projection, penv),  returnType);
                case LIST -> typeUtils.isSameType(parameterizedWith("java.util.List", projection, penv),  returnType);
                case CUSTOM -> true;
            };
        });
        // Strategy-specific validations could be added here in the filter
    }

    public static String toEnumRef(TypeElement projectionClass) {
        return projectionClass.getQualifiedName().toString() + "_";
    }

    public static TypeMirror toEnumRefMirror(String enumRefFqcn, ProcessingEnvironment processingEnv) {
        return processingEnv.getElementUtils().getTypeElement(enumRefFqcn).asType();
    }

    private static TypeMirror genericFilterRequestType(String fqcnPropertyRef, ProcessingEnvironment processingEnv) {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();

        TypeElement reqElement = elementUtils.getTypeElement(FilterRequest.class.getName());
        TypeElement refElement = elementUtils.getTypeElement(fqcnPropertyRef);
        return typeUtils.getDeclaredType(reqElement, refElement.asType());
    }

    public static TypeMirror parameterizedWith(String parameterizedFqcn, TypeElement typeUsed, ProcessingEnvironment penv) {
        Elements elementUtils = penv.getElementUtils();
        Types typeUtils = penv.getTypeUtils();

        TypeElement parameterizedElement = elementUtils.getTypeElement(parameterizedFqcn);
        return typeUtils.getDeclaredType(parameterizedElement, typeUsed.asType());
    }
}
