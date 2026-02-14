    @PostMapping("${basePath}/search/${exposedName}")
    public ${endpointReturn} ${methodName}(@RequestBody FilterRequest<${fqEnumName}> req) {
        ${reqTransformation}
        // Get results.
        return ${handler};
    }

