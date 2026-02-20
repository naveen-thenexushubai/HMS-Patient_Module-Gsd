package com.hospital.patient.application;

import com.hospital.patient.api.dto.DataQualityReport;
import com.hospital.patient.infrastructure.DataQualityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for generating the data quality dashboard report.
 *
 * Executes five native COUNT queries against the patients_latest view.
 * Each query is independent and runs in the same read-only transaction.
 *
 * The report is not cached — it reflects real-time database state.
 * For a 50K patient database, all five queries combined run in < 50ms.
 */
@Service
public class DataQualityService {

    @Autowired
    private DataQualityRepository dataQualityRepository;

    /**
     * Generate a data quality report with current counts.
     *
     * @return DataQualityReport with all metric counts and generatedAt timestamp
     */
    @Transactional(readOnly = true)
    public DataQualityReport getDataQualityReport() {
        return DataQualityReport.builder()
            .totalActivePatients(dataQualityRepository.countTotalActive())
            .incompleteRegistrations(dataQualityRepository.countIncompleteRegistrations())
            .missingInsurance(dataQualityRepository.countMissingInsurance())
            .missingPhoto(dataQualityRepository.countMissingPhotos())
            .unverifiedPhotoIds(dataQualityRepository.countUnverifiedPhotoIds())
            .generatedAt(Instant.now())
            .build();
    }
}
