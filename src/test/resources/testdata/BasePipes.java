package io.github.cyfko.example;

import io.github.cyfko.filterql.core.model.FilterRequest;

// Classe de base avec pipes réutilisables
public class BasePipes {
    public static FilterRequest<PersonDTO_> tenantIsolation(FilterRequest<PersonDTO_> filter) {
        // Ex: return filter.and("tenantId", SecurityContext.getTenantId());
        return filter;
    }
    
    public static FilterRequest<PersonDTO_> softDelete(FilterRequest<PersonDTO_> filter) {
        // Ex: return filter.and("deleted", false);
        return filter;
    }
}