package com.hospital.patient.api;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.application.AppointmentService;
import com.hospital.security.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "CREATE", resourceType = "APPOINTMENT")
    public ResponseEntity<AppointmentDto> bookAppointment(
        @Valid @RequestBody BookAppointmentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(appointmentService.bookAppointment(request));
    }

    @GetMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "APPOINTMENT")
    public ResponseEntity<AppointmentDto> getAppointment(
        @PathVariable UUID appointmentId
    ) {
        return ResponseEntity.ok(appointmentService.getAppointmentByBusinessId(appointmentId));
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'ADMIN')")
    @Audited(action = "UPDATE", resourceType = "APPOINTMENT")
    public ResponseEntity<AppointmentDto> updateStatus(
        @PathVariable UUID appointmentId,
        @Valid @RequestBody AppointmentStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
            appointmentService.updateAppointmentStatus(appointmentId, request));
    }

    @GetMapping("/patients/{businessId}/appointments")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "APPOINTMENT")
    public ResponseEntity<List<AppointmentDto>> getPatientAppointments(
        @PathVariable UUID businessId
    ) {
        return ResponseEntity.ok(appointmentService.getPatientAppointments(businessId));
    }

    @GetMapping("/doctors/{doctorId}/available-slots")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Audited(action = "READ", resourceType = "DOCTOR_AVAILABILITY")
    public ResponseEntity<AvailableSlotsResponse> getAvailableSlots(
        @PathVariable String doctorId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(AvailableSlotsResponse.builder()
            .doctorId(doctorId)
            .date(date)
            .availableSlots(appointmentService.getAvailableSlots(doctorId, date))
            .build());
    }

    @PostMapping("/doctors/{doctorId}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "CREATE", resourceType = "DOCTOR_AVAILABILITY")
    public ResponseEntity<DoctorAvailabilityDto> setAvailability(
        @PathVariable String doctorId,
        @Valid @RequestBody SetAvailabilityRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(appointmentService.setDoctorAvailability(doctorId, request));
    }

    @GetMapping("/doctors/{doctorId}/availability")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Audited(action = "READ", resourceType = "DOCTOR_AVAILABILITY")
    public ResponseEntity<List<DoctorAvailabilityDto>> getAvailability(
        @PathVariable String doctorId
    ) {
        return ResponseEntity.ok(appointmentService.getDoctorAvailability(doctorId));
    }
}
