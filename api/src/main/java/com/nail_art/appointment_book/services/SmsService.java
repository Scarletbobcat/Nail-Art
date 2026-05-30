package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
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
    private final OrganizationRepository organizationRepository;
    private final SmsDeliveryGateway smsDeliveryGateway;
    private final TwilioCredentialsService twilioCredentialsService;

    public SmsService(
            AppointmentService appointmentService,
            OrganizationSettingsRepository organizationSettingsRepository,
            OrganizationRepository organizationRepository,
            SmsDeliveryGateway smsDeliveryGateway,
            TwilioCredentialsService twilioCredentialsService
    ) {
        this.appointmentService = appointmentService;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.organizationRepository = organizationRepository;
        this.smsDeliveryGateway = smsDeliveryGateway;
        this.twilioCredentialsService = twilioCredentialsService;
    }

    @Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")
    public void sendReminders() {
        List<OrganizationSettings> settings = organizationSettingsRepository.findBySmsRemindersEnabledTrue();
        RunSummary summary = new RunSummary();
        for (OrganizationSettings setting : settings) {
            summary.orgsProcessed++;
            try {
                TenantContext.runAs(setting.getOrganizationId(), () -> sendForCurrentOrg(summary));
            } catch (Exception e) {
                // A decrypt failure (wrong/mismatched APP_ENCRYPTION_KEY) lands here as a
                // LOUD error — never silently conflated with the quiet incomplete-creds skip.
                summary.orgsFailed++;
                log.error("SMS reminders failed for org {}", setting.getOrganizationId(), e);
            }
        }
        TenantContext.clear();
        // One-line per-run summary so a total silent outage (botched cutover, key mismatch)
        // is detectable from logs alone — the system has no other reminder observability.
        log.info("SMS reminder run complete: {} orgs processed, {} skipped (incomplete config), "
                        + "{} sent, {} failed, {} orgs errored",
                summary.orgsProcessed, summary.orgsSkipped, summary.messagesSent,
                summary.messagesFailed, summary.orgsFailed);
    }

    private void sendForCurrentOrg(RunSummary summary) {
        // Source the salon's own name and phone for THIS org, so a tenant's clients are never
        // told they are hearing from a different salon. Organization is the tenant root (no
        // @TenantId), keyed by the current context's organization id.
        Organization organization = organizationRepository.findById(TenantContext.get()).orElseThrow();
        String salonName = organization.getName();
        String salonPhone = organization.getBusinessPhone();

        // Per-org Twilio credentials. A wrong key makes this RAISE (propagates to the loud
        // outer catch); a quietly incomplete config is a deliberate skip, not an error.
        TwilioCredentials credentials = twilioCredentialsService.findForOrganization(TenantContext.get());
        if (!credentials.isComplete()) {
            summary.orgsSkipped++;
            log.warn("Skipping SMS reminders for org {} - Twilio config incomplete", TenantContext.get());
            return;
        }

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
                        buildReminderMessage(appointment, salonName, salonPhone),
                        credentials
                );
                if (result == SmsDeliveryGateway.Result.SENT) {
                    summary.messagesSent++;
                    appointmentService.markReminderSent(appointment.getId());
                } else if (result == SmsDeliveryGateway.Result.FAILED) {
                    summary.messagesFailed++;
                }
            } catch (Exception e) {
                summary.messagesFailed++;
                log.warn("SMS failed for appointment {}", appointment.getId(), e);
            }
        }
    }

    private static final class RunSummary {
        private int orgsProcessed;
        private int orgsSkipped;
        private int orgsFailed;
        private int messagesSent;
        private int messagesFailed;
    }

    public static String buildReminderMessage(Appointment appointment, String salonName, String salonPhone) {
        String day = appointment.getStartsAt().getDayOfWeek().toString();
        day = day.charAt(0) + day.substring(1).toLowerCase();
        String callToAction = (salonPhone == null || salonPhone.isBlank())
                ? "Please call the salon if you "
                : "Please call the salon at " + salonPhone + " if you ";
        return "Hello! This is " + salonName + ", and this is a reminder for your appointment on "
                + day + ", "
                + appointment.getStartsAt().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))
                + ". " + callToAction
                + "need to reschedule or cancel. We look forward to seeing you!\n\n"
                + "Reply STOP to stop receiving messages from this number.";
    }
}
