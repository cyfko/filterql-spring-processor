package io.github.cyfko.example;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;

/**
 * AdminRightResolver - Defines static virtual field resolvers for Person entity
 */
public class AdminRightResolver {

    /**
     * Virtual field to filter admin users
     */
    @ExposedAs(
            value = "IS_ADMIN",
            operators = {Op.EQ}
    )
    public static PredicateResolver<Person> isAdminUser(String op, Object[] args) {
        return (root, query, cb) -> {
            Boolean isAdmin = args.length > 0 ? (Boolean) args[0] : false;
            if (Boolean.TRUE.equals(isAdmin)) {
                // Filter for admin users
                return cb.equal(root.get("username"), "admin");
            } else {
                // Filter for non-admin users
                return cb.notEqual(root.get("username"), "admin");
            }
        };
    }

    public PaginatedData<AddressDTO> handleAddressSearch(FilterRequest<AddressDTO_> req){
        // Do something
        return null;
    }
}
