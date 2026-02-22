package com.hospital.patient.application;

import com.hospital.patient.api.dto.CreatePrescriptionRequest;
import com.hospital.patient.api.dto.PrescriptionDto;
import com.hospital.patient.domain.Prescription;
import com.hospital.patient.domain.PrescriptionStatus;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.patient.infrastructure.PrescriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PrescriptionService {

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PatientRepository patientRepository;

    public PrescriptionDto createPrescription(UUID patientBusinessId, CreatePrescriptionRequest request) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        Prescription prescription = Prescription.builder()
            .patientBusinessId(patientBusinessId)
            .appointmentBusinessId(request.getAppointmentBusinessId())
            .medicationName(request.getMedicationName())
            .genericName(request.getGenericName())
            .dosage(request.getDosage())
            .frequency(request.getFrequency())
            .durationDays(request.getDurationDays())
            .quantityDispensed(request.getQuantityDispensed())
            .refillsRemaining(request.getRefillsRemaining() != null ? request.getRefillsRemaining() : 0)
            .instructions(request.getInstructions())
            .expiresAt(request.getExpiresAt())
            .prescribedBy(getCurrentUsername())
            .build();

        return toDto(prescriptionRepository.save(prescription));
    }

    public PrescriptionDto discontinuePrescription(UUID businessId, String reason) {
        Prescription prescription = prescriptionRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new EntityNotFoundException("Prescription not found: " + businessId));

        prescription.setStatus(PrescriptionStatus.DISCONTINUED);
        prescription.setDiscontinueReason(reason);

        return toDto(prescriptionRepository.save(prescription));
    }

    public PrescriptionDto requestRefill(UUID businessId) {
        Prescription prescription = prescriptionRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new EntityNotFoundException("Prescription not found: " + businessId));

        if (prescription.getRefillsRemaining() == null || prescription.getRefillsRemaining() <= 0) {
            throw new IllegalArgumentException("No refills remaining");
        }

        prescription.setRefillsRemaining(prescription.getRefillsRemaining() - 1);
        return toDto(prescriptionRepository.save(prescription));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDto> getPrescriptions(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return prescriptionRepository.findByPatientBusinessIdOrderByPrescribedAtDesc(patientBusinessId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDto> getActivePrescriptions(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return prescriptionRepository.findByPatientBusinessIdAndStatus(patientBusinessId, PrescriptionStatus.ACTIVE)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }

    private PrescriptionDto toDto(Prescription p) {
        return PrescriptionDto.builder()
            .businessId(p.getBusinessId())
            .patientBusinessId(p.getPatientBusinessId())
            .appointmentBusinessId(p.getAppointmentBusinessId())
            .medicationName(p.getMedicationName())
            .genericName(p.getGenericName())
            .dosage(p.getDosage())
            .frequency(p.getFrequency())
            .durationDays(p.getDurationDays())
            .quantityDispensed(p.getQuantityDispensed())
            .refillsRemaining(p.getRefillsRemaining())
            .instructions(p.getInstructions())
            .status(p.getStatus())
            .prescribedBy(p.getPrescribedBy())
            .prescribedAt(p.getPrescribedAt())
            .expiresAt(p.getExpiresAt())
            .discontinueReason(p.getDiscontinueReason())
            .createdAt(p.getCreatedAt())
            .createdBy(p.getCreatedBy())
            .updatedAt(p.getUpdatedAt())
            .updatedBy(p.getUpdatedBy())
            .build();
    }
}
