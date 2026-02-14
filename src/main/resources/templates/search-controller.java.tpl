package io.github.cyfko.filterql.spring.controller;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

${annotationsImports}

@Generated("io.github.cyfko.filterql.spring.processor.ExposureAnnotationProcessor")
@RestController
public class FilterQlController {
    private FilterQlService searchService;
    private InstanceResolver instanceResolver;

    public FilterQlController(FilterQlService filterQlService, InstanceResolver instanceResolver) {
        this.searchService = filterQlService;
        this.instanceResolver = instanceResolver;
    }

    ${searchEndpoints}
}