package io.github.cyfko.example;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.projection.Method;
import io.github.cyfko.projection.Projection;

/**
 * DTO for Geometry entity.
 */
@Projection(from = Geometry.class)
@Exposure(
        value = "geometries",
        basePath = "/api/v1",
        strategy = Exposure.Strategy.CUSTOM,
        handler = @Method(value = "handleGeometrySearch")
)
public class GeometryDTO {
    private Long id;

    public static boolean handleGeometrySearch(FilterRequest<GeometryDTO_> req){
        // Do something
        return true;
    }
}
