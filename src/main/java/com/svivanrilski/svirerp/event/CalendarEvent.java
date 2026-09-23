package com.svivanrilski.svirerp.event;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.svivanrilski.svirerp.person.Person;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base event record for all organisation events.
 * Church-specific details extend this via ChurchEvent (one-to-one).
 * Volunteer hours optionally reference a calendar event.
 */
@Entity
@Table(name = "calendar_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Nullable: set to NULL when the creating person record is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Person createdBy;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType = "general";

    @NotNull
    @Column(name = "start_datetime", nullable = false)
    private OffsetDateTime startDatetime;

    @Column(name = "end_datetime")
    private OffsetDateTime endDatetime;

    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay = false;

    @Column(length = 255)
    private String location;

    @Column(name = "virtual_link", length = 500)
    private String virtualLink;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Column(name = "recurrence_rule", length = 255)
    private String recurrenceRule;

    /** Allowed values (DB CHECK): scheduled, cancelled, completed, postponed. */
    @Column(nullable = false, length = 30)
    private String status = "scheduled";

    /** Allowed values (DB CHECK): public, members_only, internal. */
    @Column(nullable = false, length = 30)
    private String visibility = "public";

    private Integer capacity;

    // One-way push to Google Calendar — see GoogleCalendarService. The
    // *_event_id columns are set once a push succeeds (needed to target
    // later updates/deletes at the right Google-side event); *_sync_error
    // is non-null only when the most recent push attempt failed.
    @Column(name = "publish_to_official", nullable = false)
    private Boolean publishToOfficial = false;

    @Column(name = "google_official_event_id", length = 255)
    private String googleOfficialEventId;

    @Column(name = "google_official_sync_error", columnDefinition = "TEXT")
    private String googleOfficialSyncError;

    @Column(name = "publish_to_internal", nullable = false)
    private Boolean publishToInternal = false;

    @Column(name = "google_internal_event_id", length = 255)
    private String googleInternalEventId;

    @Column(name = "google_internal_sync_error", columnDefinition = "TEXT")
    private String googleInternalSyncError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
