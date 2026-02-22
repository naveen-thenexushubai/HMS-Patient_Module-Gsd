package com.hospital.patient.domain;

public enum ConsentType {
    HIPAA_NOTICE,
    TREATMENT_AUTHORIZATION,
    FINANCIAL_RESPONSIBILITY,
    RESEARCH_PARTICIPATION,
    PHOTOGRAPHY_PERMISSION;

    public boolean isRequiredForEncounter() {
        return this == HIPAA_NOTICE
            || this == TREATMENT_AUTHORIZATION
            || this == FINANCIAL_RESPONSIBILITY;
    }
}
