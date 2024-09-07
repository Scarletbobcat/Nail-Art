package com.nail_art.appointment_book.Repositories;

import com.nail_art.appointment_book.Models.Appointment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends MongoRepository<Appointment, String> {
    Optional<Appointment> findById(int id);

    List<Appointment> findByDate(String date);

    List<Appointment> findByPhoneNumber(String phoneNumber);
}
