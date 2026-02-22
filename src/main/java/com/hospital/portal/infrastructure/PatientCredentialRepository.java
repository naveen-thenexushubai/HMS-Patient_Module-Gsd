package com.hospital.portal.infrastructure;

import com.hospital.portal.domain.PatientCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientCredentialRepository extends JpaRepository<PatientCredential, Long> {

    Optional<PatientCredential> findByEmail(String email);

    Optional<PatientCredential> findByPatientBusinessId(UUID patientBusinessId);

    boolean existsByPatientBusinessId(UUID patientBusinessId);

    boolean existsByEmail(String email);
}
