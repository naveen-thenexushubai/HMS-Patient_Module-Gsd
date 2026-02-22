package com.hospital.patient.application;

import com.hospital.patient.api.dto.FamilyMemberDto;
import com.hospital.patient.api.dto.HouseholdResponse;
import com.hospital.patient.api.dto.LinkFamilyRequest;
import com.hospital.patient.domain.Patient;
import com.hospital.patient.domain.PatientFamily;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientFamilyRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class FamilyService {

    @Autowired
    private PatientFamilyRepository familyRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Links a patient to a household.
     * If request.householdId is null, creates a new household with this patient as head.
     * Otherwise joins an existing household.
     *
     * @throws PatientNotFoundException if the patient doesn't exist
     * @throws IllegalStateException    if the patient is already in a household
     */
    public HouseholdResponse linkToFamily(UUID patientBusinessId, LinkFamilyRequest request) {
        Patient patient = patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        if (familyRepository.existsByPatientBusinessId(patientBusinessId)) {
            throw new IllegalStateException(
                "Patient " + patientBusinessId + " is already in a household. Unlink first.");
        }

        UUID householdId;
        boolean isHead;

        if (request.getHouseholdId() == null) {
            // Creating a new household — this patient is the head
            householdId = UUID.randomUUID();
            isHead = true;
        } else {
            householdId = request.getHouseholdId();
            isHead = Boolean.TRUE.equals(request.getIsHead());
        }

        PatientFamily entry = PatientFamily.builder()
            .householdId(householdId)
            .patientBusinessId(patientBusinessId)
            .relationshipToHead(request.getRelationshipToHead())
            .isHead(isHead)
            .build();

        familyRepository.save(entry);

        return buildHouseholdResponse(householdId);
    }

    /**
     * Returns the household of a given patient.
     *
     * @throws PatientNotFoundException if the patient is not in any household
     */
    @Transactional(readOnly = true)
    public HouseholdResponse getFamily(UUID patientBusinessId) {
        PatientFamily entry = familyRepository.findByPatientBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(
                "Patient " + patientBusinessId + " is not linked to any household"));
        return buildHouseholdResponse(entry.getHouseholdId());
    }

    /**
     * Returns all members of a household by householdId.
     *
     * @throws PatientNotFoundException if no members found for this householdId
     */
    @Transactional(readOnly = true)
    public HouseholdResponse getHouseholdMembers(UUID householdId) {
        List<PatientFamily> members = familyRepository.findByHouseholdId(householdId);
        if (members.isEmpty()) {
            throw new PatientNotFoundException("Household " + householdId + " not found");
        }
        return buildHouseholdResponseFromMembers(householdId, members);
    }

    /**
     * Removes a patient from their household.
     * If the patient was the head and others remain, the member with the oldest record
     * (lowest id) is promoted to head.
     *
     * @throws PatientNotFoundException if the patient is not in any household
     */
    public void unlinkFromFamily(UUID patientBusinessId) {
        PatientFamily entry = familyRepository.findByPatientBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(
                "Patient " + patientBusinessId + " is not linked to any household"));

        UUID householdId = entry.getHouseholdId();
        boolean wasHead = Boolean.TRUE.equals(entry.getIsHead());
        familyRepository.delete(entry);

        if (wasHead) {
            List<PatientFamily> remaining = familyRepository.findByHouseholdId(householdId);
            if (!remaining.isEmpty()) {
                // Promote the longest-standing member (lowest id) to head
                PatientFamily newHead = remaining.stream()
                    .min(Comparator.comparing(PatientFamily::getId))
                    .orElseThrow();
                newHead.setIsHead(true);
                newHead.setRelationshipToHead("HEAD");
                familyRepository.save(newHead);
            }
        }
    }

    private HouseholdResponse buildHouseholdResponse(UUID householdId) {
        List<PatientFamily> members = familyRepository.findByHouseholdId(householdId);
        return buildHouseholdResponseFromMembers(householdId, members);
    }

    private HouseholdResponse buildHouseholdResponseFromMembers(UUID householdId, List<PatientFamily> members) {
        List<FamilyMemberDto> memberDtos = members.stream()
            .map(m -> {
                String fullName = patientRepository.findLatestVersionByBusinessId(m.getPatientBusinessId())
                    .map(p -> p.getFirstName() + " " + p.getLastName())
                    .orElse("Unknown");
                String patientId = patientRepository.findLatestVersionByBusinessId(m.getPatientBusinessId())
                    .map(Patient::getPatientId)
                    .orElse(null);
                return FamilyMemberDto.builder()
                    .patientBusinessId(m.getPatientBusinessId())
                    .patientId(patientId)
                    .fullName(fullName)
                    .relationshipToHead(m.getRelationshipToHead())
                    .isHead(m.getIsHead())
                    .joinedAt(m.getCreatedAt())
                    .build();
            })
            .collect(Collectors.toList());

        return HouseholdResponse.builder()
            .householdId(householdId)
            .members(memberDtos)
            .memberCount(memberDtos.size())
            .build();
    }
}
