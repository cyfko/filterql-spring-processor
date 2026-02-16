package io.github.cyfko.example;

import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Method;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Provider;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * DTO for Address entity.
 */
@Projection(
        from = Address.class,
        providers = @Provider(VirtualFields.class)
)
@Exposure(
        value = "addresses",
        basePath = "/api/v1",
        strategy = Exposure.Strategy.PAGINATED,
        handler = @Method(value = "handleAddressSearch", type = AdminRightResolver.class)
)
public interface AddressDTO {
    Long getId();

    @ExposedAs(value = "STREET", operators = {Op.EQ, Op.MATCHES, Op.NE})
    String getStreet();

    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getCity();

    @ExposedAs(value = "ZIP_CODE", operators = {Op.EQ, Op.MATCHES})
    String getZipCode();

    @ExposedAs(value = "COUNTRY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getCountry();
}
