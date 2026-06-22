package com.ticketsystem.it_service_backend.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encodes/decodes the assignment-target marker embedded in an {@code ASSIGN} audit
 * log note.
 *
 * <p>The {@code ticket_audit_logs} table has no column for the agent a ticket was
 * assigned <em>to</em> (only {@code actor_id}, the assigner). Rather than a schema
 * migration, the target agent's ID is prefixed into the otherwise free-form
 * {@code note} as {@code [[assignee:<uuid>]]} at write time and stripped at read
 * time. The name is then resolved live during DTO assembly (same batched lookup as
 * actor names), so it stays correct even if the agent is later renamed.
 */
public final class AssignAuditNote {

    private AssignAuditNote() {
    }

    private static final Pattern MARKER = Pattern.compile("^\\[\\[assignee:([^\\]]+)\\]\\]");

    /**
     * Builds the stored note value: the assignee marker followed by the (optional)
     * free-form admin note.
     *
     * @param targetAgentId the agent the ticket was assigned to
     * @param adminNote the admin's optional note (may be null)
     * @return the note value to persist
     */
    public static String encode(String targetAgentId, String adminNote) {
        return "[[assignee:" + targetAgentId + "]]" + (adminNote == null ? "" : adminNote);
    }

    /**
     * Extracts the assignee agent ID from a stored note, or null if the marker is
     * absent (non-assign entries, or assign rows written before this feature).
     */
    public static String extractAgentId(String note) {
        if (note == null) {
            return null;
        }
        Matcher matcher = MARKER.matcher(note);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Removes the assignee marker, leaving only the human-facing note. Returns null
     * when nothing remains (marker-only note). No-op for notes without a marker.
     */
    public static String stripMarker(String note) {
        if (note == null) {
            return null;
        }
        String stripped = MARKER.matcher(note).replaceFirst("");
        return stripped.isEmpty() ? null : stripped;
    }
}
