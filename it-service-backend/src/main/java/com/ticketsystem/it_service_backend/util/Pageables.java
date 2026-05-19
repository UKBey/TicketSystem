package com.ticketsystem.it_service_backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Controller'larda tekrar eden "sortDir oku → Sort yarat → PageRequest sar" zincirini
 * tek satırda toplayan helper. {@code sortDir} sadece {@code "asc"} ise artan; diğer
 * tüm değerler için (default dahil) azalan sıralama uygulanır.
 */
public final class Pageables {

    private Pageables() {}

    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}
