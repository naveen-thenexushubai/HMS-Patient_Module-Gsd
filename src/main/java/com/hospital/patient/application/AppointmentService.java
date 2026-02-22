package com.hospital.patient.application;

import com.hospital.patient.api.dto.*;
import com.hospital.patient.domain.*;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.infrastructure.AppointmentRepository;
import com.hospital.patient.infrastructure.DoctorAvailabilityRepository;
import com.hospital.patient.infrastructure.PatientRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Book a new appointment.
     * Validates: patient exists, slot is not already taken.
     * Computes end time from doctor's slot duration configuration.
     */
    public AppointmentDto bookAppointment(BookAppointmentRequest request) {
        // Verify patient exists
        patientRepository.findLatestVersionByBusinessId(request.getPatientBusinessId())
            .orElseThrow(() -> new PatientNotFoundException(request.getPatientBusinessId().toString()));

        // Check slot not taken
        if (appointmentRepository.isSlotTaken(request.getDoctorId(), request.getAppointmentDate(), request.getStartTime())) {
            throw new IllegalArgumentException("The requested time slot is already booked for doctor: " + request.getDoctorId());
        }

        // Compute end time from doctor availability (slot duration)
        LocalTime endTime = computeEndTime(request.getDoctorId(), request.getAppointmentDate(), request.getStartTime());

        Appointment appointment = Appointment.builder()
            .patientBusinessId(request.getPatientBusinessId())
            .doctorId(request.getDoctorId())
            .appointmentDate(request.getAppointmentDate())
            .startTime(request.getStartTime())
            .endTime(endTime)
            .type(request.getType())
            .status(AppointmentStatus.SCHEDULED)
            .notes(request.getNotes())
            .build();

        return toDto(appointmentRepository.save(appointment));
    }

    /**
     * Retrieve appointment detail by businessId.
     */
    @Transactional(readOnly = true)
    public AppointmentDto getAppointmentByBusinessId(UUID businessId) {
        return appointmentRepository.findByBusinessId(businessId)
            .map(this::toDto)
            .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + businessId));
    }

    /**
     * Update appointment status with transition validation.
     * Terminal states (COMPLETED, CANCELLED, NO_SHOW) cannot be transitioned out of.
     */
    public AppointmentDto updateAppointmentStatus(UUID businessId, AppointmentStatusUpdateRequest request) {
        Appointment appointment = appointmentRepository.findByBusinessId(businessId)
            .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + businessId));

        if (appointment.getStatus().isTerminal()) {
            throw new IllegalStateException(
                "Cannot change status of an appointment in terminal state: " + appointment.getStatus());
        }

        if (request.getStatus() == AppointmentStatus.CANCELLED && request.getCancelReason() != null) {
            appointment.setCancelReason(request.getCancelReason());
        }
        appointment.setStatus(request.getStatus());

        return toDto(appointmentRepository.save(appointment));
    }

    /**
     * Get all appointments for a patient ordered by date descending.
     */
    @Transactional(readOnly = true)
    public List<AppointmentDto> getPatientAppointments(UUID patientBusinessId) {
        return appointmentRepository.findByPatientBusinessId(patientBusinessId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get upcoming appointments for a patient (next 30 days, active statuses only).
     * Used by PatientService to populate PatientDetailResponse.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSummaryDto> getUpcomingAppointments(UUID patientBusinessId) {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(30);
        return appointmentRepository.findUpcomingByPatientBusinessId(patientBusinessId, today, until)
            .stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    /**
     * Get available time slots for a doctor on a specific date.
     * Generates all slots from availability window, excludes already-booked ones.
     */
    @Transactional(readOnly = true)
    public List<LocalTime> getAvailableSlots(String doctorId, LocalDate date) {
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return availabilityRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, dayOfWeek)
            .map(avail -> generateFreeSlots(doctorId, date, avail))
            .orElse(List.of());
    }

    /**
     * Set doctor availability for a day of week.
     * Deactivates any existing active availability for the same doctor+dayOfWeek.
     */
    public DoctorAvailabilityDto setDoctorAvailability(String doctorId, SetAvailabilityRequest request) {
        // Deactivate existing availability for this doctor+day
        availabilityRepository.deactivateByDoctorAndDay(doctorId, request.getDayOfWeek());

        int slotDuration = request.getSlotDurationMinutes() != null ? request.getSlotDurationMinutes() : 30;

        DoctorAvailability availability = DoctorAvailability.builder()
            .doctorId(doctorId)
            .dayOfWeek(request.getDayOfWeek())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .slotDurationMinutes(slotDuration)
            .isActive(true)
            .build();

        return toAvailabilityDto(availabilityRepository.save(availability));
    }

    /**
     * Get all active availability windows for a doctor.
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityDto> getDoctorAvailability(String doctorId) {
        return availabilityRepository.findByDoctorIdAndIsActiveTrue(doctorId)
            .stream().map(this::toAvailabilityDto).collect(Collectors.toList());
    }

    /**
     * Cancel all future SCHEDULED/CONFIRMED appointments for a patient.
     * Called by PatientUpdatedEventListener when patient goes INACTIVE.
     * Uses REQUIRES_NEW because listener runs AFTER_COMMIT (outside original transaction).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelFutureAppointmentsForInactivePatient(UUID patientBusinessId) {
        return appointmentRepository.cancelFutureAppointments(
            patientBusinessId,
            LocalDate.now(),
            "Patient account deactivated"
        );
    }

    // --- Private helpers ---

    private LocalTime computeEndTime(String doctorId, LocalDate date, LocalTime startTime) {
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return availabilityRepository.findByDoctorIdAndDayOfWeekAndIsActiveTrue(doctorId, dayOfWeek)
            .map(avail -> startTime.plusMinutes(avail.getSlotDurationMinutes()))
            .orElse(startTime.plusMinutes(30)); // default 30 min if no availability configured
    }

    private List<LocalTime> generateFreeSlots(String doctorId, LocalDate date, DoctorAvailability avail) {
        List<LocalTime> free = new ArrayList<>();
        LocalTime cursor = avail.getStartTime();
        while (cursor.plusMinutes(avail.getSlotDurationMinutes()).compareTo(avail.getEndTime()) <= 0) {
            if (!appointmentRepository.isSlotTaken(doctorId, date, cursor)) {
                free.add(cursor);
            }
            cursor = cursor.plusMinutes(avail.getSlotDurationMinutes());
        }
        return free;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "system";
    }

    private AppointmentDto toDto(Appointment a) {
        return AppointmentDto.builder()
            .id(a.getId())
            .businessId(a.getBusinessId())
            .patientBusinessId(a.getPatientBusinessId())
            .doctorId(a.getDoctorId())
            .appointmentDate(a.getAppointmentDate())
            .startTime(a.getStartTime())
            .endTime(a.getEndTime())
            .type(a.getType())
            .status(a.getStatus())
            .notes(a.getNotes())
            .cancelReason(a.getCancelReason())
            .createdAt(a.getCreatedAt())
            .createdBy(a.getCreatedBy())
            .updatedAt(a.getUpdatedAt())
            .updatedBy(a.getUpdatedBy())
            .build();
    }

    private AppointmentSummaryDto toSummaryDto(Appointment a) {
        return AppointmentSummaryDto.builder()
            .businessId(a.getBusinessId())
            .doctorId(a.getDoctorId())
            .appointmentDate(a.getAppointmentDate())
            .startTime(a.getStartTime())
            .endTime(a.getEndTime())
            .type(a.getType())
            .status(a.getStatus())
            .build();
    }

    private DoctorAvailabilityDto toAvailabilityDto(DoctorAvailability da) {
        return DoctorAvailabilityDto.builder()
            .id(da.getId())
            .doctorId(da.getDoctorId())
            .dayOfWeek(da.getDayOfWeek())
            .startTime(da.getStartTime())
            .endTime(da.getEndTime())
            .slotDurationMinutes(da.getSlotDurationMinutes())
            .isActive(da.getIsActive())
            .build();
    }
}
