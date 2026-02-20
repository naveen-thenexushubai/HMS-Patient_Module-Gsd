package com.hospital.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published after a patient record is successfully updated (new version inserted).
 * Published via ApplicationEventPublisher; delivered AFTER transaction commit via
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT).
 *
 * Carries businessId + version to allow downstream consumers to re-fetch if needed.
 * changedFields lists which fields changed (e.g., ["firstName", "status"]).
 */
public class PatientUpdatedEvent {

    private final Object source;
    private final UUID businessId;
    private final Long newVersion;
    private final String updatedBy;
    private final Instant occurredAt;
    private final List<String> changedFields;

    public PatientUpdatedEvent(Object source, UUID businessId, Long newVersion,
                               String updatedBy, List<String> changedFields) {
        this.source = source;
        this.businessId = businessId;
        this.newVersion = newVersion;
        this.updatedBy = updatedBy;
        this.occurredAt = Instant.now();
        this.changedFields = changedFields != null ? changedFields : List.of();
    }

    public Object getSource()           { return source; }
    public UUID getBusinessId()         { return businessId; }
    public Long getNewVersion()         { return newVersion; }
    public String getUpdatedBy()        { return updatedBy; }
    public Instant getOccurredAt()      { return occurredAt; }
    public List<String> getChangedFields() { return changedFields; }
}
