package io.github.cyfko.example;

import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Projection;

/**
 * DTO for Segment entity.
 */
@Projection(from = Segment.class)
@Exposure(
        value = "segments",
        basePath = "/api/v1",
        strategy = Exposure.Strategy.LIST
)
public class SegmentDTO {
    private Long id;
}
