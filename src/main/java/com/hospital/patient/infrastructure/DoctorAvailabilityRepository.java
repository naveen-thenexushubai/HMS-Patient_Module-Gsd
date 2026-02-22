package com.hospital.patient.infrastructure;

import com.hospital.patient.domain.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    List<DoctorAvailability> findByDoctorIdAndIsActiveTrue(String doctorId);

    Optional<DoctorAvailability> findByDoctorIdAndDayOfWeekAndIsActiveTrue(
        String doctorId, DayOfWeek dayOfWeek
    );

    @Modifying
    @Query("UPDATE DoctorAvailability d SET d.isActive = false WHERE d.doctorId = :doctorId AND d.dayOfWeek = :dayOfWeek AND d.isActive = true")
    void deactivateByDoctorAndDay(@Param("doctorId") String doctorId, @Param("dayOfWeek") DayOfWeek dayOfWeek);
}
