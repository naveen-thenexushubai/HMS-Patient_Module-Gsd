package com.hospital.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener for PatientUpdatedEvent.
 *
 * Uses @TransactionalEventListener(AFTER_COMMIT) to guarantee the new patient
 * version row is committed and readable before this listener executes.
 * Uses @Async so listener work does not block the HTTP response thread.
 *
 * IMPORTANT: Do NOT write to the database inside this listener without
 * @Transactional(propagation = REQUIRES_NEW) — AFTER_COMMIT runs outside
 * the original transaction context.
 *
 * Future: Replace log.info with Kafka/RabbitMQ publish when external broker is added.
 */
@Component
public class PatientUpdatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PatientUpdatedEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientUpdated(PatientUpdatedEvent event) {
        log.info("PatientUpdated event: businessId={}, version={}, updatedBy={}, fields={}",
            event.getBusinessId(),
            event.getNewVersion(),
            event.getUpdatedBy(),
            event.getChangedFields());
        // Future Phase: forward to external message broker here
    }
}
