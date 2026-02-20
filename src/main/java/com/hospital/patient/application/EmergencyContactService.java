package com.hospital.patient.application;

import com.hospital.patient.api.dto.EmergencyContactDto;
import com.hospital.patient.domain.EmergencyContact;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.EmergencyContactRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmergencyContactService {

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Get the current authenticated username from Spring Security context.
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }

    /**
     * Add a new emergency contact for a patient.
     * Validates that the patient exists (latest version found by businessId).
     */
    public EmergencyContactDto addContact(UUID businessId, EmergencyContactDto dto) {
        // Verify patient exists
        patientRepository.findLatestVersionByBusinessId(businessId)
            .orElseThrow(() -> new PatientNotFoundException(businessId.toString()));

        EmergencyContact contact = EmergencyContact.builder()
            .patientBusinessId(businessId)
            .name(dto.getName())
            .phoneNumber(dto.getPhoneNumber())
            .relationship(dto.getRelationship())
            .isPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : false)
            .createdBy(getCurrentUsername())
            .build();

        EmergencyContact saved = emergencyContactRepository.save(contact);
        return toDto(saved);
    }

    /**
     * Update an existing emergency contact.
     * CRITICAL: Verifies that contact.patientBusinessId == businessId from the URL
     * to prevent cross-patient modification (Pitfall 6 from research).
     */
    public EmergencyContactDto updateContact(UUID businessId, Long contactId, EmergencyContactDto dto) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Emergency contact not found: " + contactId));

        // Ownership check — prevent cross-patient modification
        if (!contact.getPatientBusinessId().equals(businessId)) {
            throw new AccessDeniedException(
                "Emergency contact " + contactId + " does not belong to patient " + businessId);
        }

        contact.setName(dto.getName());
        contact.setPhoneNumber(dto.getPhoneNumber());
        contact.setRelationship(dto.getRelationship());
        if (dto.getIsPrimary() != null) {
            contact.setIsPrimary(dto.getIsPrimary());
        }

        EmergencyContact saved = emergencyContactRepository.save(contact);
        return toDto(saved);
    }

    /**
     * Delete an emergency contact by ID.
     * CRITICAL: Verifies ownership before delete (same Pitfall 6 protection).
     */
    public void deleteContact(UUID businessId, Long contactId) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Emergency contact not found: " + contactId));

        if (!contact.getPatientBusinessId().equals(businessId)) {
            throw new AccessDeniedException(
                "Emergency contact " + contactId + " does not belong to patient " + businessId);
        }

        emergencyContactRepository.deleteById(contactId);
    }

    /**
     * List all emergency contacts for a patient (primary contact first).
     */
    @Transactional(readOnly = true)
    public List<EmergencyContactDto> listContacts(UUID businessId) {
        return emergencyContactRepository
            .findByPatientBusinessIdOrderByIsPrimaryDesc(businessId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    private EmergencyContactDto toDto(EmergencyContact contact) {
        return EmergencyContactDto.builder()
            .id(contact.getId())
            .name(contact.getName())
            .phoneNumber(contact.getPhoneNumber())
            .relationship(contact.getRelationship())
            .isPrimary(contact.getIsPrimary())
            .build();
    }
}
