package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.AppointmentServiceLink;
import com.nail_art.appointment_book.entities.AppointmentServiceLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentServiceLinkRepository extends JpaRepository<AppointmentServiceLink, AppointmentServiceLinkId> {
    @Override
    @Query("select l from AppointmentServiceLink l where l.id = :id")
    Optional<AppointmentServiceLink> findById(AppointmentServiceLinkId id);

    List<AppointmentServiceLink> findByIdAppointmentId(UUID appointmentId);

    void deleteByIdAppointmentId(UUID appointmentId);
}
