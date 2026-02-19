package com.hospital.patient.application;

import com.hospital.patient.domain.Patient;
import com.hospital.patient.infrastructure.PatientRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.codec.language.Soundex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Service for detecting duplicate patient records using multi-field fuzzy matching.
 *
 * Algorithm:
 * 1. Fast filter by exact DOB (reduces 10K patients to ~10-50 candidates)
 * 2. Multi-field weighted similarity scoring:
 *    - Name: 30% (Levenshtein distance + Soundex phonetic boost)
 *    - DOB: 40% (exact match, already filtered)
 *    - Phone: 20% (normalized comparison)
 *    - Email: 10% (case-insensitive exact match)
 *
 * Thresholds:
 * - 85-89%: Warning (returns 409 Conflict, requires overrideDuplicate=true)
 * - 90%+: Block (requires admin approval, not implemented in this phase)
 */
@Service
public class DuplicateDetectionService {

    @Autowired
    private PatientRepository patientRepository;

    // Thresholds from research: 85% = warning, 90% = block
    private static final double WARNING_THRESHOLD = 0.85;
    private static final double BLOCKING_THRESHOLD = 0.90;

    /**
     * Check for duplicate patients based on registration request data.
     *
     * @param firstName First name from registration request
     * @param lastName Last name from registration request
     * @param dateOfBirth Date of birth from registration request
     * @param phoneNumber Phone number from registration request
     * @param email Email from registration request
     * @return DuplicateCheckResult containing matches and blocking status
     */
    public DuplicateCheckResult checkForDuplicates(
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String phoneNumber,
            String email) {

        // Step 1: Fast filter by exact DOB (reduces 10K to ~10-50 candidates)
        List<Patient> candidates = patientRepository
            .findLatestVersionsByDateOfBirth(dateOfBirth);

        if (candidates.isEmpty()) {
            return DuplicateCheckResult.noDuplicates();
        }

        // Step 2: Fuzzy matching on filtered candidates
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        Soundex soundex = new Soundex();

        List<DuplicateMatch> matches = new ArrayList<>();

        for (Patient candidate : candidates) {
            double score = calculateSimilarityScore(
                firstName, lastName, phoneNumber, email,
                candidate, levenshtein, soundex
            );

            if (score >= WARNING_THRESHOLD) {
                matches.add(new DuplicateMatch(candidate, score));
            }
        }

        if (matches.isEmpty()) {
            return DuplicateCheckResult.noDuplicates();
        }

        // Sort by score descending
        matches.sort(Comparator.comparingDouble(DuplicateMatch::getScore).reversed());

        // Block if highest score >= 90%
        boolean shouldBlock = matches.get(0).getScore() >= BLOCKING_THRESHOLD;

        return new DuplicateCheckResult(matches, shouldBlock);
    }

    /**
     * Calculate weighted similarity score across multiple fields.
     *
     * @param requestFirstName First name from request
     * @param requestLastName Last name from request
     * @param requestPhone Phone number from request
     * @param requestEmail Email from request
     * @param candidate Existing patient record
     * @param levenshtein Levenshtein distance calculator
     * @param soundex Soundex phonetic encoder
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateSimilarityScore(
        String requestFirstName,
        String requestLastName,
        String requestPhone,
        String requestEmail,
        Patient candidate,
        LevenshteinDistance levenshtein,
        Soundex soundex
    ) {
        double totalScore = 0.0;
        int weightSum = 0;

        // Name similarity (weight: 30%)
        String requestFullName = (requestFirstName + " " + requestLastName).toLowerCase();
        String candidateFullName = (candidate.getFirstName() + " " + candidate.getLastName()).toLowerCase();

        int maxLength = Math.max(requestFullName.length(), candidateFullName.length());
        int distance = levenshtein.apply(requestFullName, candidateFullName);
        double nameSimilarity = 1.0 - ((double) distance / maxLength);

        // Boost for phonetic match
        try {
            boolean phoneticMatch = soundex.encode(requestLastName)
                .equals(soundex.encode(candidate.getLastName()));
            if (phoneticMatch) {
                nameSimilarity = Math.max(nameSimilarity, 0.8);
            }
        } catch (Exception e) {
            // Soundex encoding can fail for very short names or special characters
            // Continue without phonetic boost
        }

        totalScore += nameSimilarity * 30;
        weightSum += 30;

        // DOB exact match (weight: 40%) - already filtered
        totalScore += 1.0 * 40;
        weightSum += 40;

        // Phone number similarity (weight: 20%)
        if (requestPhone != null && candidate.getPhoneNumber() != null) {
            String normalizedRequest = normalizePhoneNumber(requestPhone);
            String normalizedCandidate = normalizePhoneNumber(candidate.getPhoneNumber());
            if (normalizedRequest.equals(normalizedCandidate)) {
                totalScore += 1.0 * 20;
            }
            weightSum += 20;
        }

        // Email exact match (weight: 10%)
        if (requestEmail != null && candidate.getEmail() != null) {
            if (requestEmail.equalsIgnoreCase(candidate.getEmail())) {
                totalScore += 1.0 * 10;
            }
            weightSum += 10;
        }

        return totalScore / weightSum;
    }

    /**
     * Normalize phone number by removing all non-digit characters.
     * E.g., "(555) 123-4567" becomes "5551234567"
     */
    private String normalizePhoneNumber(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * Result of duplicate check containing matches and blocking status.
     */
    @Data
    @AllArgsConstructor
    public static class DuplicateCheckResult {
        private List<DuplicateMatch> matches;
        private boolean shouldBlockRegistration;

        public static DuplicateCheckResult noDuplicates() {
            return new DuplicateCheckResult(Collections.emptyList(), false);
        }

        public boolean hasDuplicates() {
            return !matches.isEmpty();
        }
    }

    /**
     * A single duplicate match with similarity score.
     */
    @Data
    @AllArgsConstructor
    public static class DuplicateMatch {
        private Patient patient;
        private double score;
    }
}
