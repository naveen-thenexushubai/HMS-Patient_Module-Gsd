package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, Long> {

    List<ClinicalNote> findByPatientBusinessIdOrderByCreatedAtDesc(UUID patientBusinessId);

    Optional<ClinicalNote> findByBusinessId(UUID businessId);

    List<ClinicalNote> findByPatientBusinessIdAndFinalized(UUID patientBusinessId, boolean finalized);
}
