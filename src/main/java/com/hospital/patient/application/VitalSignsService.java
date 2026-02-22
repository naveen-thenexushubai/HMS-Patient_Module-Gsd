package com.hospital.patient.application;

import com.hospital.patient.api.dto.RecordVitalSignsRequest;
import com.hospital.patient.api.dto.VitalSignsDto;
import com.hospital.patient.domain.VitalSigns;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.PatientRepository;
import com.hospital.patient.infrastructure.VitalSignsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class VitalSignsService {

    @Autowired
    private VitalSignsRepository vitalSignsRepository;

    @Autowired
    private PatientRepository patientRepository;

    public VitalSignsDto recordVitalSigns(UUID patientBusinessId, RecordVitalSignsRequest request) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        VitalSigns vitals = VitalSigns.builder()
            .patientBusinessId(patientBusinessId)
            .appointmentBusinessId(request.getAppointmentBusinessId())
            .temperature(request.getTemperature())
            .systolicBp(request.getSystolicBp())
            .diastolicBp(request.getDiastolicBp())
            .heartRate(request.getHeartRate())
            .respiratoryRate(request.getRespiratoryRate())
            .oxygenSaturation(request.getOxygenSaturation())
            .weightKg(request.getWeightKg())
            .heightCm(request.getHeightCm())
            .notes(request.getNotes())
            .build();

        VitalSigns saved = vitalSignsRepository.save(vitals);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<VitalSignsDto> getVitalSigns(UUID patientBusinessId, int page, int size) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return vitalSignsRepository.findByPatientBusinessIdOrderByRecordedAtDesc(
            patientBusinessId, PageRequest.of(page, size)).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<VitalSignsDto> getLatestVitalSigns(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return vitalSignsRepository.findTopByPatientBusinessIdOrderByRecordedAtDesc(patientBusinessId)
            .map(this::toDto);
    }

    private VitalSignsDto toDto(VitalSigns v) {
        BigDecimal bmi = null;
        if (v.getWeightKg() != null && v.getHeightCm() != null
                && v.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = v.getHeightCm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            bmi = v.getWeightKg().divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
        }
        return VitalSignsDto.builder()
            .businessId(v.getBusinessId())
            .patientBusinessId(v.getPatientBusinessId())
            .appointmentBusinessId(v.getAppointmentBusinessId())
            .recordedAt(v.getRecordedAt())
            .temperature(v.getTemperature())
            .systolicBp(v.getSystolicBp())
            .diastolicBp(v.getDiastolicBp())
            .heartRate(v.getHeartRate())
            .respiratoryRate(v.getRespiratoryRate())
            .oxygenSaturation(v.getOxygenSaturation())
            .weightKg(v.getWeightKg())
            .heightCm(v.getHeightCm())
            .bmi(bmi)
            .notes(v.getNotes())
            .createdAt(v.getCreatedAt())
            .createdBy(v.getCreatedBy())
            .build();
    }
}
