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

    /**
     * Tek satirda sirali bir {@link PageRequest} olusturur.
     *
     * @param page    sifir-tabanli sayfa indexi
     * @param size    sayfa basina kayit sayisi
     * @param sortBy  siralama yapilacak alan adi (entity alani)
     * @param sortDir {@code "asc"} → artan, diger her sey (default dahil) → azalan
     * @return verilen alan ve yone gore siralanmis sayfa istegi
     */
    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}
