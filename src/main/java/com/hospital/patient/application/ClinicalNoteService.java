package com.hospital.patient.application;

import com.hospital.patient.api.dto.ClinicalNoteDto;
import com.hospital.patient.api.dto.CreateClinicalNoteRequest;
import com.hospital.patient.api.dto.UpdateClinicalNoteRequest;
import com.hospital.patient.domain.ClinicalNote;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.ClinicalNoteRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClinicalNoteService {

    @Autowired
    private ClinicalNoteRepository clinicalNoteRepository;

    @Autowired
    private PatientRepository patientRepository;

    public ClinicalNoteDto createNote(UUID patientBusinessId, CreateClinicalNoteRequest request) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        ClinicalNote note = ClinicalNote.builder()
            .patientBusinessId(patientBusinessId)
            .appointmentBusinessId(request.getAppointmentBusinessId())
            .noteType(request.getNoteType())
            .subjective(request.getSubjective())
            .objective(request.getObjective())
            .assessment(request.getAssessment())
            .plan(request.getPlan())
            .build();

        return toDto(clinicalNoteRepository.save(note));
    }

    public ClinicalNoteDto updateNote(UUID noteBusinessId, UpdateClinicalNoteRequest request) {
        ClinicalNote note = clinicalNoteRepository.findByBusinessId(noteBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Clinical note not found: " + noteBusinessId));

        if (note.isFinalized()) {
            throw new IllegalStateException("Note is finalized and cannot be edited");
        }

        note.setSubjective(request.getSubjective());
        note.setObjective(request.getObjective());
        note.setAssessment(request.getAssessment());
        note.setPlan(request.getPlan());

        return toDto(clinicalNoteRepository.save(note));
    }

    public ClinicalNoteDto finalizeNote(UUID noteBusinessId) {
        ClinicalNote note = clinicalNoteRepository.findByBusinessId(noteBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Clinical note not found: " + noteBusinessId));

        note.setFinalized(true);
        note.setFinalizedAt(Instant.now());
        note.setFinalizedBy(getCurrentUsername());

        return toDto(clinicalNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<ClinicalNoteDto> getNotes(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return clinicalNoteRepository.findByPatientBusinessIdOrderByCreatedAtDesc(patientBusinessId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClinicalNoteDto getNoteByBusinessId(UUID noteBusinessId) {
        ClinicalNote note = clinicalNoteRepository.findByBusinessId(noteBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Clinical note not found: " + noteBusinessId));
        return toDto(note);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }

    private ClinicalNoteDto toDto(ClinicalNote n) {
        return ClinicalNoteDto.builder()
            .businessId(n.getBusinessId())
            .patientBusinessId(n.getPatientBusinessId())
            .appointmentBusinessId(n.getAppointmentBusinessId())
            .noteType(n.getNoteType())
            .subjective(n.getSubjective())
            .objective(n.getObjective())
            .assessment(n.getAssessment())
            .plan(n.getPlan())
            .finalized(n.isFinalized())
            .finalizedAt(n.getFinalizedAt())
            .finalizedBy(n.getFinalizedBy())
            .createdAt(n.getCreatedAt())
            .createdBy(n.getCreatedBy())
            .updatedAt(n.getUpdatedAt())
            .updatedBy(n.getUpdatedBy())
            .build();
    }
}
