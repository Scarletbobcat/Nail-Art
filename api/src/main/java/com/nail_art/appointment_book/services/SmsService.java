package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final AppointmentService appointmentService;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final SmsDeliveryGateway smsDeliveryGateway;

    public SmsService(
            AppointmentService appointmentService,
            OrganizationSettingsRepository organizationSettingsRepository,
            SmsDeliveryGateway smsDeliveryGateway
    ) {
        this.appointmentService = appointmentService;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.smsDeliveryGateway = smsDeliveryGateway;
    }

    @Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")
    public void sendReminders() {
        List<OrganizationSettings> settings = organizationSettingsRepository.findBySmsRemindersEnabledTrue();
        for (OrganizationSettings setting : settings) {
            try {
                TenantContext.runAs(setting.getOrganizationId(), this::sendForCurrentOrg);
            } catch (Exception e) {
                log.error("SMS reminders failed for org {}", setting.getOrganizationId(), e);
            }
        }
        TenantContext.clear();
    }

    private void sendForCurrentOrg() {
        for (Appointment appointment : appointmentService.getAppointmentsForTomorrow()) {
            try {
                if (appointment.getReminderSent()) {
                    continue;
                }
                if (appointment.getPhoneNumber() == null || appointment.getPhoneNumber().isEmpty()) {
                    continue;
                }
                SmsDeliveryGateway.Result result = smsDeliveryGateway.sendReminder(
                        appointment.getPhoneNumber(),
                        buildReminderMessage(appointment)
                );
                if (result == SmsDeliveryGateway.Result.SENT) {
                    appointmentService.markReminderSent(appointment.getId());
                }
            } catch (Exception e) {
                log.warn("SMS failed for appointment {}", appointment.getId(), e);
            }
        }
    }

    public static String buildReminderMessage(Appointment appointment) {
        String day = appointment.getStartsAt().getDayOfWeek().toString();
        day = day.charAt(0) + day.substring(1).toLowerCase();
        return "Hello! This is Nail Art & Spa LLC., and this is a reminder for your appointment on "
                + day + ", "
                + appointment.getStartsAt().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))
                + ". Please call the salon at 330-758-6633 if you "
                + "need to reschedule or cancel. We look forward to seeing you!\n\n"
                + "Reply STOP to stop receiving messages from this number.";
    }
}
