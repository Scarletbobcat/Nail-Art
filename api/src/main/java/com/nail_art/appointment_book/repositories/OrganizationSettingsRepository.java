package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {
    List<OrganizationSettings> findBySmsRemindersEnabledTrue();
}
