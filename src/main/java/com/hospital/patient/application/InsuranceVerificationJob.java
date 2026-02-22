package com.hospital.patient.application;

import com.hospital.patient.api.dto.InsuranceVerificationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduled job for bulk insurance verification.
 * Runs at 2:00 AM daily. Requires @EnableScheduling (see SchedulingConfig).
 */
@Component
public class InsuranceVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(InsuranceVerificationJob.class);

    @Autowired
    private InsuranceVerificationService insuranceVerificationService;

    /**
     * Runs nightly at 2:00 AM to verify all active insurance records.
     * Updates verification_status and last_verified_at for each record.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyVerification() {
        log.info("Starting nightly insurance verification job");
        try {
            InsuranceVerificationSummary summary = insuranceVerificationService.verifyAll();
            log.info("Nightly insurance verification complete: processed={}, verified={}, incomplete={}, stale={}, pending={}",
                summary.getProcessedCount(), summary.getVerifiedCount(),
                summary.getIncompleteCount(), summary.getStaleCount(), summary.getPendingCount());
        } catch (Exception e) {
            log.error("Nightly insurance verification job failed", e);
        }
    }
}
