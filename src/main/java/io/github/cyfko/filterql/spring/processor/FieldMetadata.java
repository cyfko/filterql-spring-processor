package io.github.cyfko.filterql.spring.processor;

import java.util.Arrays;
import java.util.function.Function;

import io.github.cyfko.filterql.core.api.Op;

/**
 * Metadata for a filterable field (regular or virtual).
 * <p>
 * This record holds the configuration derived from the entity and @Projection,
 * including the field name, allowed operators, and resolver details for virtual
 * fields.
 * </p>
 */
public record FieldMetadata(
                String referredAs,
                String name,
                Op[] operators,
                VirtualFieldDetails virtualFieldDetails) {
        /**
         * Creates metadata for a regular JPA field.
         *
         * @param refName   the exposed name (e.g. used in the API)
         * @param name      the actual field name in the entity
         * @param operators allowed operators
         * @return a new FieldMetadata instance
         */
        public static FieldMetadata regularField(
                        String refName,
                        String name,
                        Op[] operators) {
                return new FieldMetadata(refName, name, operators, null);
        }

        /**
         * Creates metadata for a virtual field exposed via a provider.
         *
         * @param refName           the exposed name
         * @param methodName        the resolver method name
         * @param operators         allowed operators
         * @param resolverClassName the class containing the resolver
         * @param beanName          the Spring bean name (if not static)
         * @param isStatic          true if the resolver method is static
         * @return a new FieldMetadata instance
         */
        public static FieldMetadata virtualField(
                        String refName,
                        String methodName,
                        Op[] operators,
                        String resolverClassName,
                        String beanName,
                        boolean isStatic) {
                return new FieldMetadata(
                                refName,
                                methodName,
                                operators,
                                new VirtualFieldDetails(resolverClassName, beanName, isStatic));
        }

        /**
         * Checks if this field is virtual.
         *
         * @return true if the field has virtual field details, false otherwise
         */
        public boolean isVirtual() {
                return virtualFieldDetails != null;
        }

        /**
         * Generates a Java source code representation of this metadata.
         * Use to embed metadata in generated code.
         *
         * @return the Java code string instantiating this metadata
         */
        public String toSourceCode() {
                String ops = operators == null || operators.length == 0
                                ? "new Op[]{}"
                                : "new Op[]{" + String.join(", ",
                                                Arrays.stream(operators).map(op -> "Op." + op.name()).toList()) + "}";

                Function<String, String> safe = s -> s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";

                if (isVirtual()) {
                        var v = virtualFieldDetails;
                        return String.format(
                                        "FieldMetadata.virtualField(%s, SupportedType.%s, %s, %s, %s)",
                                        safe.apply(referredAs),
                                        name,
                                        ops,
                                        safe.apply(v.resolverClassName()),
                                        v.isStatic());
                } else {
                        return String.format(
                                        "FieldMetadata.regularField(%s, SupportedType.%s, %s)",
                                        safe.apply(referredAs),
                                        name,
                                        ops);
                }
        }

        /**
         * Details for virtual fields.
         *
         * @param resolverClassName Fully qualified class name containing the resolver
         *                          method
         * @param beanName          Spring bean name (null for static methods)
         * @param isStatic          Whether the method is static
         */
        public record VirtualFieldDetails(String resolverClassName, String beanName, boolean isStatic) {
        }
}