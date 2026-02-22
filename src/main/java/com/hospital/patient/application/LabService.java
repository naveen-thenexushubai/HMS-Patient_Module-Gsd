package com.hospital.patient.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.LabOrder;
import com.hospital.patient.domain.LabOrderStatus;
import com.hospital.patient.domain.LabResult;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.LabOrderRepository;
import com.hospital.patient.infrastructure.LabResultRepository;
import com.hospital.patient.infrastructure.PatientRepository;
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
public class LabService {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private LabResultRepository labResultRepository;

    @Autowired
    private PatientRepository patientRepository;

    public LabOrderDto createLabOrder(UUID patientBusinessId, CreateLabOrderRequest request) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));

        LabOrder order = LabOrder.builder()
            .patientBusinessId(patientBusinessId)
            .appointmentBusinessId(request.getAppointmentBusinessId())
            .orderName(request.getOrderName())
            .orderedBy(getCurrentUsername())
            .priority(request.getPriority())
            .notes(request.getNotes())
            .build();

        LabOrder saved = labOrderRepository.save(order);
        List<LabResult> results = labResultRepository.findByLabOrderBusinessId(saved.getBusinessId());
        return toOrderDto(saved, results);
    }

    public LabOrderDto updateOrderStatus(UUID orderBusinessId, LabOrderStatus newStatus) {
        LabOrder order = labOrderRepository.findByBusinessId(orderBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Lab order not found: " + orderBusinessId));

        order.setStatus(newStatus);
        LabOrder saved = labOrderRepository.save(order);
        List<LabResult> results = labResultRepository.findByLabOrderBusinessId(saved.getBusinessId());
        return toOrderDto(saved, results);
    }

    public LabResultDto addLabResult(UUID orderBusinessId, AddLabResultRequest request) {
        LabOrder order = labOrderRepository.findByBusinessId(orderBusinessId)
            .orElseThrow(() -> new EntityNotFoundException("Lab order not found: " + orderBusinessId));

        LabResult result = LabResult.builder()
            .labOrderBusinessId(order.getBusinessId())
            .patientBusinessId(order.getPatientBusinessId())
            .testName(request.getTestName())
            .resultValue(request.getResultValue())
            .unit(request.getUnit())
            .referenceRange(request.getReferenceRange())
            .abnormal(request.isAbnormal())
            .abnormalFlag(request.getAbnormalFlag())
            .resultText(request.getResultText())
            .build();

        LabResult saved = labResultRepository.save(result);

        if (order.getStatus() != LabOrderStatus.COMPLETED && order.getStatus() != LabOrderStatus.CANCELLED) {
            order.setStatus(LabOrderStatus.COMPLETED);
            labOrderRepository.save(order);
        }

        return toResultDto(saved);
    }

    @Transactional(readOnly = true)
    public List<LabOrderDto> getLabOrdersWithResults(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return labOrderRepository.findByPatientBusinessIdOrderByCreatedAtDesc(patientBusinessId)
            .stream()
            .map(order -> {
                List<LabResult> results = labResultRepository.findByLabOrderBusinessId(order.getBusinessId());
                return toOrderDto(order, results);
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LabResultDto> getAbnormalResults(UUID patientBusinessId) {
        patientRepository.findLatestVersionByBusinessId(patientBusinessId)
            .orElseThrow(() -> new PatientNotFoundException(patientBusinessId.toString()));
        return labResultRepository.findByPatientBusinessIdAndAbnormalTrue(patientBusinessId)
            .stream().map(this::toResultDto).collect(Collectors.toList());
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }

    private LabOrderDto toOrderDto(LabOrder o, List<LabResult> results) {
        return LabOrderDto.builder()
            .businessId(o.getBusinessId())
            .patientBusinessId(o.getPatientBusinessId())
            .appointmentBusinessId(o.getAppointmentBusinessId())
            .orderName(o.getOrderName())
            .orderedBy(o.getOrderedBy())
            .status(o.getStatus())
            .priority(o.getPriority())
            .notes(o.getNotes())
            .createdAt(o.getCreatedAt())
            .createdBy(o.getCreatedBy())
            .updatedAt(o.getUpdatedAt())
            .updatedBy(o.getUpdatedBy())
            .results(results.stream().map(this::toResultDto).collect(Collectors.toList()))
            .build();
    }

    private LabResultDto toResultDto(LabResult r) {
        return LabResultDto.builder()
            .businessId(r.getBusinessId())
            .labOrderBusinessId(r.getLabOrderBusinessId())
            .patientBusinessId(r.getPatientBusinessId())
            .testName(r.getTestName())
            .resultValue(r.getResultValue())
            .unit(r.getUnit())
            .referenceRange(r.getReferenceRange())
            .abnormal(r.isAbnormal())
            .abnormalFlag(r.getAbnormalFlag())
            .resultText(r.getResultText())
            .documentFilename(r.getDocumentFilename())
            .reviewedBy(r.getReviewedBy())
            .reviewedAt(r.getReviewedAt())
            .createdAt(r.getCreatedAt())
            .createdBy(r.getCreatedBy())
            .build();
    }
}
