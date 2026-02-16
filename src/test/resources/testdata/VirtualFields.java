package io.github.cyfko.example;

import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.spring.ExposedAs;
import jakarta.persistence.criteria.Predicate;


public class VirtualFields {

    @ExposedAs(
            value = "FULL_NAME",
            operators = {Op.MATCHES}
    )
    public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
        return (root, query, cb) -> {
            if (args.length == 0) return cb.conjunction();

            String searchTerm = (String) args[0];
            String pattern = "%" + searchTerm + "%";
            Predicate firstName = cb.like(root.get("firstName"), pattern);
            Predicate lastName = cb.like(root.get("lastName"), pattern);
            return cb.or(firstName, lastName);
        };
    }

    @ExposedAs(
            value = "IS_ADMIN",
            operators = {Op.EQ}
    )
    public PredicateResolver<Person> isAdminUser(String op, Object[] args) {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Virtual field: Full name (static method)
     * Searches in an area defined by its WKB geom.
     */
    @ExposedAs(
            value = "WITHIN_GEOMETRY",
            operators = {Op.MATCHES}
    )
    public static PredicateResolver<Address> addressInGeometryArea(String op, Object[] args) {
        return (root, query, cb) -> cb.conjunction();
    }

 }