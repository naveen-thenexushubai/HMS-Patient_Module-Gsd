package com.hospital.patient.infrastructure;

import com.hospital.patient.api.dto.PatientSummaryResponse;
import com.hospital.patient.domain.Gender;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.apache.commons.codec.language.Metaphone;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for patient search using JPQL queries.
 * NOTE: Hibernate Search with Lucene deferred due to test context configuration issues.
 * This implementation uses JPQL LIKE queries for Phase 1. Lucene full-text search
 * will be implemented in Phase 3 after resolving @DataJpaTest indexing compatibility.
 */
@Repository
public class PatientSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Search patients with query and filters using JPQL.
     *
     * @param query Search query (searches name, phone, email, patient ID)
     * @param status Filter by patient status
     * @param gender Filter by gender
     * @param bloodGroup Filter by blood group (joins medical_histories table)
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
        // Build JPQL query with dynamic predicates
        // Only return the latest version of each patient (max patientId per businessId)
        StringBuilder jpql = new StringBuilder(
            "SELECT p FROM Patient p WHERE p.patientId IN " +
            "(SELECT max(p2.patientId) FROM Patient p2 GROUP BY p2.businessId) AND 1=1"
        );

        List<String> predicates = new ArrayList<>();

        // Search query - matches name, phone, email, or patient ID
        if (query != null && !query.isBlank()) {
            String searchPattern = "%" + query.toLowerCase() + "%";
            predicates.add("(LOWER(p.firstName) LIKE :query OR LOWER(p.lastName) LIKE :query " +
                           "OR LOWER(p.phoneNumber) LIKE :query OR LOWER(p.email) LIKE :query " +
                           "OR LOWER(p.patientId) LIKE :query)");
        }

        // Status filter
        if (status != null) {
            predicates.add("p.status = :status");
        }

        // Gender filter
        if (gender != null) {
            predicates.add("p.gender = :gender");
        }

        // Blood group filter (requires join with medical_histories)
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            predicates.add("p.businessId IN (SELECT mh.patientBusinessId FROM MedicalHistory mh WHERE mh.bloodGroup = :bloodGroup)");
        }

        // Add predicates to query
        for (String predicate : predicates) {
            jpql.append(" AND ").append(predicate);
        }

        // Add sorting
        if (query != null && !query.isBlank()) {
            // When searching, sort by relevance approximation (exact matches first)
            jpql.append(" ORDER BY CASE " +
                       "WHEN LOWER(p.patientId) = LOWER(:queryExact) THEN 1 " +
                       "WHEN LOWER(p.firstName) = LOWER(:queryExact) OR LOWER(p.lastName) = LOWER(:queryExact) THEN 2 " +
                       "ELSE 3 END, p.lastName, p.firstName");
        } else {
            // Default sort by name
            jpql.append(" ORDER BY p.lastName, p.firstName, p.patientId");
        }

        // Create typed query
        TypedQuery<Patient> typedQuery = entityManager.createQuery(jpql.toString(), Patient.class);

        // Set parameters
        if (query != null && !query.isBlank()) {
            typedQuery.setParameter("query", "%" + query.toLowerCase() + "%");
            typedQuery.setParameter("queryExact", query.toLowerCase());
        }
        if (status != null) {
            typedQuery.setParameter("status", status);
        }
        if (gender != null) {
            typedQuery.setParameter("gender", gender);
        }
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            typedQuery.setParameter("bloodGroup", bloodGroup);
        }

        // Apply pagination - fetch size+1 for hasNext detection
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize() + 1);

        List<Patient> patients = new ArrayList<>(typedQuery.getResultList());

        // Fuzzy second pass: add name-matching candidates missed by LIKE
        // Only runs when query looks like a name (no digits, no @ symbol)
        if (query != null && !query.isBlank() && isNameLikeQuery(query)) {
            List<Patient> fuzzyMatches = fuzzyNameSearch(query, status, gender, bloodGroup, 2);
            // Deduplicate: add fuzzy results not already in LIKE results
            java.util.Set<String> existingIds = patients.stream()
                .map(Patient::getPatientId)
                .collect(java.util.stream.Collectors.toSet());
            for (Patient fuzzy : fuzzyMatches) {
                if (!existingIds.contains(fuzzy.getPatientId())) {
                    patients.add(fuzzy);
                    existingIds.add(fuzzy.getPatientId());
                }
            }
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
     * Phonetic search using Soundex and Metaphone encoding.
     * Finds patients whose first or last name sounds like the query.
     * Useful for finding "Johnson" when querying "Jonson", "Johnston", "Johnsen".
     *
     * Fetches all candidates (with optional status/gender/bloodGroup filters),
     * then filters in-memory using phonetic encoding comparison.
     *
     * @param query Name to match phonetically
     * @param status Optional status filter
     * @param gender Optional gender filter
     * @param bloodGroup Optional blood group filter
     * @param pageable Pagination parameters
     * @return Slice of phonetically matching patient summaries
     */
    public Slice<PatientSummaryResponse> phoneticSearch(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        Pageable pageable
    ) {
        // Fetch all candidates with filters (no name filter — phonetic matching done in-memory)
        // Only return latest version of each patient
        StringBuilder jpql = new StringBuilder(
            "SELECT p FROM Patient p WHERE p.patientId IN " +
            "(SELECT max(p2.patientId) FROM Patient p2 GROUP BY p2.businessId) AND 1=1"
        );
        List<String> predicates = new ArrayList<>();

        if (status != null) predicates.add("p.status = :status");
        if (gender != null) predicates.add("p.gender = :gender");
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            predicates.add("p.businessId IN (SELECT mh.patientBusinessId FROM MedicalHistory mh WHERE mh.bloodGroup = :bloodGroup)");
        }
        for (String pred : predicates) jpql.append(" AND ").append(pred);
        jpql.append(" ORDER BY p.lastName, p.firstName");

        TypedQuery<Patient> tq = entityManager.createQuery(jpql.toString(), Patient.class);
        if (status != null) tq.setParameter("status", status);
        if (gender != null) tq.setParameter("gender", gender);
        if (bloodGroup != null && !bloodGroup.isBlank()) tq.setParameter("bloodGroup", bloodGroup);

        List<Patient> all = tq.getResultList();

        // Phonetic encode the query using both Soundex and Metaphone
        Soundex soundex = new Soundex();
        Metaphone metaphone = new Metaphone();

        String querySoundex = null;
        String queryMetaphone = null;

        if (query != null && !query.isBlank()) {
            try { querySoundex = soundex.encode(query); } catch (Exception ignored) {}
            try { queryMetaphone = metaphone.encode(query); } catch (Exception ignored) {}
        }

        final String finalQuerySoundex = querySoundex;
        final String finalQueryMetaphone = queryMetaphone;

        // Filter in-memory: keep patients whose name sounds like the query
        List<Patient> matches = all.stream()
            .filter(p -> {
                if (finalQuerySoundex == null && finalQueryMetaphone == null) return true;
                String firstSoundex = null, lastSoundex = null;
                String firstMeta = null, lastMeta = null;
                try { firstSoundex = soundex.encode(p.getFirstName()); } catch (Exception ignored) {}
                try { lastSoundex = soundex.encode(p.getLastName()); } catch (Exception ignored) {}
                try { firstMeta = metaphone.encode(p.getFirstName()); } catch (Exception ignored) {}
                try { lastMeta = metaphone.encode(p.getLastName()); } catch (Exception ignored) {}

                boolean soundexMatch = finalQuerySoundex != null && (
                    finalQuerySoundex.equals(firstSoundex) || finalQuerySoundex.equals(lastSoundex)
                );
                boolean metaphoneMatch = finalQueryMetaphone != null && (
                    finalQueryMetaphone.equals(firstMeta) || finalQueryMetaphone.equals(lastMeta)
                );
                return soundexMatch || metaphoneMatch;
            })
            .collect(Collectors.toList());

        // Manual pagination
        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();

        List<Patient> page;
        boolean hasNext;
        if (offset >= matches.size()) {
            page = List.of();
            hasNext = false;
        } else {
            int end = Math.min(offset + pageSize + 1, matches.size());
            List<Patient> slice = new ArrayList<>(matches.subList(offset, end));
            hasNext = slice.size() > pageSize;
            page = hasNext ? slice.subList(0, pageSize) : slice;
        }

        List<PatientSummaryResponse> summaries = page.stream()
            .map(this::toSummaryResponse)
            .collect(Collectors.toList());

        return new SliceImpl<>(summaries, pageable, hasNext);
    }

    /**
     * Returns true if the query looks like a name (no digits, no @ symbol, at least 2 chars).
     * This guards fuzzy matching from running on phone numbers, emails, or patient IDs.
     */
    private boolean isNameLikeQuery(String query) {
        if (query == null || query.trim().length() < 2) return false;
        return !query.matches(".*\\d.*") && !query.contains("@");
    }

    /**
     * Fuzzy second-pass: find all Patient entities whose firstName or lastName
     * is within maxEditDistance Levenshtein distance of the query string.
     * Used to surface spelling variations missed by LIKE (e.g., "Jon" -> "John").
     *
     * Fetches all patients (respecting status/gender/bloodGroup filters) without pagination,
     * then filters in-memory. Safe for Phase 1 volumes (<10K patients).
     *
     * @param query Name query to match against
     * @param status Optional status filter (same as LIKE pass)
     * @param gender Optional gender filter (same as LIKE pass)
     * @param bloodGroup Optional blood group filter (same as LIKE pass)
     * @param maxEditDistance Maximum Levenshtein distance to accept (use 2 for "Jon"->"John")
     * @return List of Patient entities matching fuzzy criteria
     */
    private List<Patient> fuzzyNameSearch(
        String query,
        PatientStatus status,
        Gender gender,
        String bloodGroup,
        int maxEditDistance
    ) {
        // Only return latest version of each patient
        StringBuilder jpql = new StringBuilder(
            "SELECT p FROM Patient p WHERE p.patientId IN " +
            "(SELECT max(p2.patientId) FROM Patient p2 GROUP BY p2.businessId) AND 1=1"
        );
        List<String> predicates = new ArrayList<>();

        if (status != null) predicates.add("p.status = :status");
        if (gender != null) predicates.add("p.gender = :gender");
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            predicates.add("p.businessId IN (SELECT mh.patientBusinessId FROM MedicalHistory mh WHERE mh.bloodGroup = :bloodGroup)");
        }
        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }

        TypedQuery<Patient> tq = entityManager.createQuery(jpql.toString(), Patient.class);
        if (status != null) tq.setParameter("status", status);
        if (gender != null) tq.setParameter("gender", gender);
        if (bloodGroup != null && !bloodGroup.isBlank()) tq.setParameter("bloodGroup", bloodGroup);

        List<Patient> all = tq.getResultList();

        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        String lowerQuery = query.toLowerCase();

        return all.stream()
            .filter(p -> {
                String firstName = p.getFirstName() != null ? p.getFirstName().toLowerCase() : "";
                String lastName = p.getLastName() != null ? p.getLastName().toLowerCase() : "";
                Integer firstDist = levenshtein.apply(lowerQuery, firstName);
                Integer lastDist = levenshtein.apply(lowerQuery, lastName);
                return (firstDist != null && firstDist <= maxEditDistance)
                    || (lastDist != null && lastDist <= maxEditDistance);
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Convert Patient entity to lightweight summary response.
     */
    private PatientSummaryResponse toSummaryResponse(Patient patient) {
        return PatientSummaryResponse.builder()
            .businessId(patient.getBusinessId())
            .patientId(patient.getPatientId())
            .fullName(patient.getFirstName() + " " + patient.getLastName())
            .age(patient.getAge())
            .gender(patient.getGender())
            .phoneNumber(patient.getPhoneNumber())
            .status(patient.getStatus())
            .build();
    }
}
