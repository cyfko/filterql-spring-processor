package io.github.cyfko.example;

import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Method;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Provider;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Projection(
        from = Person.class,
        providers = {
                @Provider(AdminRightResolver.class),
                @Provider(UserTenancyResolvers.class),
                @Provider(VirtualFields.class)
        }
)
@Exposure(
        value = "users",
        basePath = "/api/v1",
        pipes = {
                @Method(type = UserPipes.class, value = "tenantIsolation"),  // Hérité
                @Method(type = UserPipes.class, value = "softDelete"),       // Hérité
                @Method(type = UserPipes.class, value = "activeUsersOnly")   // Déclaré
        }
)
public interface PersonDTO {

    Long getId();

    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.NE, Op.IN})
    String getUsername();

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES, Op.NE})
    String getEmail();

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getFirstName();

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN, Op.IS_NULL})
    String getLastName();

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    Integer getAge();

    @ExposedAs(value = "ACTIVE", operators = {Op.EQ})
    Boolean isActive();

    @ExposedAs(value = "REGISTERED_AT", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    LocalDateTime getRegisteredAt();

    @ExposedAs(value = "BIRTH_DATE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    LocalDate getBirthDate();

    AddressDTO getAddress();
}
