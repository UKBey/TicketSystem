package com.ticketsystem.it_service_backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Helper that collapses the repeated controller pattern "read sortDir →
 * build Sort → wrap into PageRequest" into a single call. Ascending order
 * is applied only when {@code sortDir} equals {@code "asc"}; every other
 * value (including the default) yields descending order.
 */
public final class Pageables {

    private Pageables() {}

    /**
     * Builds a sorted {@link PageRequest} in a single call.
     *
     * @param page    zero-based page index
     * @param size    number of records per page
     * @param sortBy  name of the entity field to sort by
     * @param sortDir {@code "asc"} → ascending, anything else (including the
     *                default) → descending
     * @return a page request sorted by the given field and direction
     */
    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}
