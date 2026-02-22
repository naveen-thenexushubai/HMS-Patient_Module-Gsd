package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByBusinessId(UUID businessId);

    @Query("SELECT a FROM Appointment a WHERE a.patientBusinessId = :patientBusinessId ORDER BY a.appointmentDate DESC, a.startTime DESC")
    List<Appointment> findByPatientBusinessId(@Param("patientBusinessId") UUID patientBusinessId);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.patientBusinessId = :patientBusinessId
          AND a.appointmentDate BETWEEN :today AND :until
          AND a.status IN (com.hospital.patient.domain.AppointmentStatus.SCHEDULED,
                           com.hospital.patient.domain.AppointmentStatus.CONFIRMED,
                           com.hospital.patient.domain.AppointmentStatus.IN_PROGRESS)
        ORDER BY a.appointmentDate ASC, a.startTime ASC
        """)
    List<Appointment> findUpcomingByPatientBusinessId(
        @Param("patientBusinessId") UUID patientBusinessId,
        @Param("today") LocalDate today,
        @Param("until") LocalDate until
    );

    @Query("""
        SELECT COUNT(a) > 0 FROM Appointment a
        WHERE a.doctorId = :doctorId
          AND a.appointmentDate = :date
          AND a.startTime = :startTime
          AND a.status NOT IN (com.hospital.patient.domain.AppointmentStatus.CANCELLED,
                               com.hospital.patient.domain.AppointmentStatus.NO_SHOW)
        """)
    boolean isSlotTaken(
        @Param("doctorId") String doctorId,
        @Param("date") LocalDate date,
        @Param("startTime") LocalTime startTime
    );

    @Modifying
    @Query("""
        UPDATE Appointment a
        SET a.status = com.hospital.patient.domain.AppointmentStatus.CANCELLED,
            a.cancelReason = :reason
        WHERE a.patientBusinessId = :patientBusinessId
          AND a.appointmentDate >= :today
          AND a.status IN (com.hospital.patient.domain.AppointmentStatus.SCHEDULED,
                           com.hospital.patient.domain.AppointmentStatus.CONFIRMED)
        """)
    int cancelFutureAppointments(
        @Param("patientBusinessId") UUID patientBusinessId,
        @Param("today") LocalDate today,
        @Param("reason") String reason
    );
}
