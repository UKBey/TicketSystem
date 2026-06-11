package com.ticketsystem.it_service_backend.util;

/**
 * Helpers for the bilingual (tr/en) name pattern used by products and ticket topics.
 *
 * <p>Records store both variants and require at least one to be present; the UI picks
 * the variant matching the active language and falls back to the other. The backend
 * itself stays language-neutral: these helpers exist only for places that need a single
 * string regardless of viewer language (logs, audit trail entries).
 */
public final class LocalizedText {

    private LocalizedText() {
    }

    /**
     * Language-independent display label for logs and audit records: the single name
     * when only one variant is set (or both are equal), otherwise {@code "tr / en"}.
     */
    public static String label(String tr, String en) {
        boolean hasEn = en != null && !en.isBlank();
        if (tr != null && !tr.isBlank()) {
            return hasEn && !tr.equals(en) ? tr + " / " + en : tr;
        }
        return hasEn ? en : null;
    }
}
