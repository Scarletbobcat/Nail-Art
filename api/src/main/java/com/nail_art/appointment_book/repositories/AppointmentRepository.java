package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Query("select a from Appointment a where a.id = :id and a.archivedAt is null")
    Optional<Appointment> findScopedById(UUID id);

    @Query("""
            select a from Appointment a
            where a.archivedAt is null
              and a.startsAt >= :start
              and a.startsAt < :end
            order by a.startsAt
            """)
    List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThan(OffsetDateTime start, OffsetDateTime end);

    @Query("""
            select a from Appointment a
            where a.archivedAt is null
              and a.phoneNumber = :phoneNumber
            order by a.startsAt
            """)
    List<Appointment> findByPhoneNumber(String phoneNumber);

    @Query("""
            select a from Appointment a
            where a.archivedAt is null
              and lower(a.phoneNumber) like lower(concat('%', :phoneNumber, '%'))
            order by a.startsAt
            """)
    List<Appointment> findByPhoneNumberContaining(String phoneNumber);

    @Query("""
            select a from Appointment a
            where a.archivedAt is null
              and a.clientId = :clientId
            order by a.startsAt
            """)
    List<Appointment> findByClientId(UUID clientId);

    @Query("""
            select a from Appointment a
            where a.archivedAt is null
              and a.employeeId = :employeeId
              and a.startsAt < :endsAt
              and a.endsAt > :startsAt
            """)
    List<Appointment> findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(
            UUID employeeId,
            OffsetDateTime endsAt,
            OffsetDateTime startsAt
    );
}
