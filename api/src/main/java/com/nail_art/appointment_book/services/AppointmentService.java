package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.AppointmentServiceLink;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.AppointmentServiceLinkRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final AppointmentServiceLinkRepository appointmentServiceLinkRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            ClientRepository clientRepository,
            OrganizationRepository organizationRepository,
            AppointmentServiceLinkRepository appointmentServiceLinkRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.clientRepository = clientRepository;
        this.organizationRepository = organizationRepository;
        this.appointmentServiceLinkRepository = appointmentServiceLinkRepository;
    }

    public List<Appointment> getAllAppointments() {
        OffsetDateTime now = OffsetDateTime.now();
        return appointmentRepository.findByStartsAtGreaterThanEqualAndStartsAtLessThan(now.minusYears(10), now.plusYears(10));
    }

    public Optional<Appointment> getAppointmentById(UUID id) {
        return appointmentRepository.findScopedById(id).map(this::attachServiceIds);
    }

    public List<Appointment> getAppointmentsByDate(String date) {
        return getAppointmentsByDate(LocalDate.parse(date));
    }

    public List<Appointment> getAppointmentsByDate(LocalDate date) {
        ZoneId salonZone = ZoneId.of(currentTimezone());
        OffsetDateTime start = date.atStartOfDay(salonZone).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(salonZone).toOffsetDateTime();
        return appointmentRepository.findByStartsAtGreaterThanEqualAndStartsAtLessThan(start, end)
                .stream()
                .map(this::attachServiceIds)
                .toList();
    }

    @Transactional
    public Appointment createAppointment(Appointment appointment) {
        checkForConflicts(appointment, null);
        linkClient(appointment);
        Appointment saved = appointmentRepository.save(appointment);
        replaceServiceLinks(saved);
        return attachServiceIds(saved);
    }

    @Transactional
    public Optional<Appointment> editAppointment(UUID id, Appointment appointment) {
        Optional<Appointment> existing = appointmentRepository.findScopedById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        checkForConflicts(appointment, id);
        Appointment target = existing.get();
        target.setCustomerName(appointment.getCustomerName());
        target.setPhoneNumber(appointment.getPhoneNumber());
        target.setEmployeeId(appointment.getEmployeeId());
        target.setStartsAt(appointment.getStartsAt());
        target.setEndsAt(appointment.getEndsAt());
        target.setShowedUp(Boolean.TRUE.equals(appointment.getShowedUp()));
        target.setServiceIds(appointment.getServiceIds());
        target.setReminderSentAt(appointment.getReminderSentAt());
        target.setArchivedAt(appointment.getArchivedAt());
        target.setClientId(appointment.getClientId());
        linkClient(target);

        Appointment saved = appointmentRepository.save(target);
        replaceServiceLinks(saved);
        return Optional.of(attachServiceIds(saved));
    }

    public Optional<Appointment> editAppointment(Appointment appointment) {
        return editAppointment(appointment.getId(), appointment);
    }

    @Transactional
    public void markReminderSent(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findScopedById(appointmentId).orElseThrow();
        appointment.setReminderSentAt(OffsetDateTime.now());
        appointmentRepository.save(appointment);
    }

    @Transactional
    public boolean deleteAppointment(UUID appointmentId) {
        Optional<Appointment> appointment = appointmentRepository.findScopedById(appointmentId);
        if (appointment.isEmpty()) {
            return false;
        }
        appointment.get().setArchivedAt(OffsetDateTime.now());
        appointmentRepository.save(appointment.get());
        return true;
    }

    public Boolean deleteAppointment(Appointment appointment) {
        return deleteAppointment(appointment.getId());
    }

    public List<Appointment> getAppointmentsByPhoneNumber(String phoneNumber) {
        return appointmentRepository.findByPhoneNumberContaining(phoneNumber)
                .stream()
                .map(this::attachServiceIds)
                .toList();
    }

    public List<Appointment> getAppointmentsForTomorrow() {
        ZoneId salonZone = ZoneId.of(currentTimezone());
        return getAppointmentsByDate(LocalDate.now(salonZone).plusDays(1));
    }

    private void checkForConflicts(Appointment candidate, UUID excludeId) {
        List<Appointment> conflicts = appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(
                candidate.getEmployeeId(),
                candidate.getEndsAt(),
                candidate.getStartsAt()
        );
        boolean hasConflict = conflicts.stream()
                .anyMatch(existing -> excludeId == null || !excludeId.equals(existing.getId()));
        if (hasConflict) {
            throw new IllegalArgumentException("Time slot conflicts with an existing appointment");
        }
    }

    private void linkClient(Appointment appointment) {
        if (appointment.getClientId() != null || appointment.getPhoneNumber() == null || appointment.getPhoneNumber().isBlank()) {
            return;
        }

        Optional<Client> existingClient = clientRepository.findByPhoneNumber(appointment.getPhoneNumber());
        Client client = existingClient == null
                ? null
                : existingClient.orElseGet(() -> {
                    Client newClient = new Client();
                    newClient.setName(appointment.getName());
                    newClient.setPhoneNumber(appointment.getPhoneNumber());
                    return clientRepository.save(newClient);
                });
        if (client == null) {
            return;
        }
        appointment.setClientId(client.getId());
        appointment.setName(client.getName());
    }

    private Appointment attachServiceIds(Appointment appointment) {
        normalizeForResponse(appointment);
        if (appointment.getId() == null || appointmentServiceLinkRepository == null) {
            return appointment;
        }
        appointment.setServiceIds(
                appointmentServiceLinkRepository.findByIdAppointmentId(appointment.getId())
                        .stream()
                        .map(AppointmentServiceLink::getServiceId)
                        .toList()
        );
        return appointment;
    }

    private void normalizeForResponse(Appointment appointment) {
        ZoneId salonZone = ZoneId.of(currentTimezone());
        if (appointment.getStartsAt() != null) {
            appointment.setStartsAt(appointment.getStartsAt().atZoneSameInstant(salonZone).toOffsetDateTime());
        }
        if (appointment.getEndsAt() != null) {
            appointment.setEndsAt(appointment.getEndsAt().atZoneSameInstant(salonZone).toOffsetDateTime());
        }
    }

    private void replaceServiceLinks(Appointment appointment) {
        UUID organizationId = TenantContext.get();
        if (appointment.getId() == null || organizationId == null || appointmentServiceLinkRepository == null) {
            return;
        }
        appointmentServiceLinkRepository.deleteByIdAppointmentId(appointment.getId());
        appointmentServiceLinkRepository.saveAll(
                appointment.getServiceIds()
                        .stream()
                        .map(serviceId -> new AppointmentServiceLink(organizationId, appointment.getId(), serviceId))
                        .toList()
        );
    }

    private String currentTimezone() {
        String timezone = organizationRepository.currentTimezone();
        return timezone == null || timezone.isBlank() ? "America/New_York" : timezone;
    }
}
