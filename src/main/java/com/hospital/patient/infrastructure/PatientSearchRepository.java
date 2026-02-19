package com.hospital.patient.infrastructure;

import com.hospital.patient.api.dto.PatientSummaryResponse;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for full-text patient search using Hibernate Search with Lucene backend.
 * Supports fuzzy name matching, wildcard searches, and multiple filter combinations.
 */
@Repository
public class PatientSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Search patients with full-text query and filters.
     *
     * @param query Full-text search query (searches name, phone, email, patient ID)
     * @param status Filter by patient status
     * @param gender Filter by gender
     * @param bloodGroup Filter by blood group (note: requires separate query due to separate table)
     * @param pageable Pagination parameters
     * @return Slice of patient summaries with hasNext indicator
     */
    public Slice<PatientSummaryResponse> searchPatients(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        Pageable pageable
    ) {
        SearchSession searchSession = Search.session(entityManager);

        // Build search query with full-text and filters
        SearchResult<Patient> result = searchSession.search(Patient.class)
            .where(f -> {
                // If no criteria at all, match everything
                if ((query == null || query.isBlank()) && status == null && gender == null) {
                    return f.matchAll();
                }

                var predicates = f.bool();

                // Full-text search on name, phone, email, patient ID
                if (query != null && !query.isBlank()) {
                    predicates.should(f.match()
                        .fields("firstName", "lastName")
                        .matching(query)
                        .fuzzy(2)); // 2-character edit distance for typo tolerance

                    // Wildcard search on phone (partial match)
                    predicates.should(f.wildcard()
                        .field("phoneNumber")
                        .matching("*" + query.replace("-", "") + "*"));

                    // Wildcard search on email (partial match)
                    if (query.contains("@") || query.contains(".")) {
                        predicates.should(f.wildcard()
                            .field("email")
                            .matching("*" + query.toLowerCase() + "*"));
                    }

                    // Exact match on patient ID
                    predicates.should(f.match()
                        .field("patientId")
                        .matching(query.toUpperCase()));
                }

                // Filter by status
                if (status != null) {
                    predicates.must(f.match()
                        .field("status")
                        .matching(status));
                }

                // Filter by gender
                if (gender != null) {
                    predicates.must(f.match()
                        .field("gender")
                        .matching(gender));
                }

                // Note: Blood group filter requires joining medical_histories table
                // This is handled separately below due to separate table architecture

                return predicates;
            })
            // Sort by relevance if query exists, otherwise by name
            .sort(f -> {
                if (query != null && !query.isBlank()) {
                    return f.score()
                        .then().field("lastName_sort")
                        .then().field("firstName_sort");
                } else {
                    return f.field("lastName_sort")
                        .then().field("firstName_sort")
                        .then().field("patientId");
                }
            })
            // Fetch size+1 for Slice hasNext detection
            .fetch(Math.toIntExact(pageable.getOffset()), pageable.getPageSize() + 1);

        List<Patient> patients = result.hits();

        // Apply blood group filter if specified (post-processing due to separate table)
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            patients = filterByBloodGroup(patients, bloodGroup);
        }

        boolean hasNext = patients.size() > pageable.getPageSize();

        if (hasNext) {
            patients = patients.subList(0, pageable.getPageSize());
        }

        // Map to summary DTOs
        List<PatientSummaryResponse> summaries = patients.stream()
            .map(this::toSummaryResponse)
            .collect(Collectors.toList());

        return new SliceImpl<>(summaries, pageable, hasNext);
    }

    /**
     * Filter patients by blood group using separate query.
     * This is necessary because medical_histories is a separate table.
     */
    private List<Patient> filterByBloodGroup(List<Patient> patients, String bloodGroup) {
        if (patients.isEmpty()) {
            return patients;
        }

        // Query medical_histories to find business_ids with matching blood group
        @SuppressWarnings("unchecked")
        List<String> matchingBusinessIds = entityManager.createQuery(
            "SELECT DISTINCT CAST(mh.patientBusinessId AS string) " +
            "FROM MedicalHistory mh " +
            "WHERE mh.bloodGroup = :bloodGroup"
        )
        .setParameter("bloodGroup", bloodGroup)
        .getResultList();

        // Filter patients whose businessId matches
        return patients.stream()
            .filter(p -> matchingBusinessIds.contains(p.getBusinessId().toString()))
            .collect(Collectors.toList());
    }

    /**
     * Convert Patient entity to lightweight summary response.
     */
    private PatientSummaryResponse toSummaryResponse(Patient patient) {
        return PatientSummaryResponse.builder()
            .patientId(patient.getPatientId())
            .fullName(patient.getFirstName() + " " + patient.getLastName())
            .age(patient.getAge())
            .gender(patient.getGender())
            .phoneNumber(patient.getPhoneNumber())
            .status(patient.getStatus())
            .build();
    }

    /**
     * Manually trigger reindexing of all patients (admin operation).
     * Should be run after initial deployment or data migration.
     */
    public void reindexAllPatients() throws InterruptedException {
        SearchSession searchSession = Search.session(entityManager);
        searchSession.massIndexer(Patient.class)
            .threadsToLoadObjects(4)
            .batchSizeToLoadObjects(25)
            .startAndWait();
    }
}
