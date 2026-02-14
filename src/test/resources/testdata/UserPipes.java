package io.github.cyfko.example;

import io.github.cyfko.filterql.core.model.FilterRequest;

// Classe spécifique qui hérite et ajoute ses propres pipes
public class UserPipes extends BasePipes {
    public FilterRequest<PersonDTO_> activeUsersOnly(FilterRequest<PersonDTO_> filter) {
        // Ex: return filter.and("active", true);
        return filter;
    }
}