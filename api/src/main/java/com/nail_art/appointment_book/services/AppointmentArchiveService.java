package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AppointmentArchiveService {
    static final int ARCHIVE_CUTOFF_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(AppointmentArchiveService.class);

    private final AppointmentRepository appointmentRepository;
    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public AppointmentArchiveService(
            AppointmentRepository appointmentRepository,
            OrganizationRepository organizationRepository,
            Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * SUN", zone = "America/New_York")
    public void archiveOldAppointments() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusDays(ARCHIVE_CUTOFF_DAYS);
        List<Organization> organizations = organizationRepository.findAll();
        RunSummary summary = new RunSummary();

        for (Organization organization : organizations) {
            summary.orgsProcessed++;
            try {
                TenantContext.runAs(organization.getId(), () -> {
                    int archived = appointmentRepository.archiveEndedBefore(organization.getId(), cutoff);
                    summary.appointmentsArchived += archived;
                    log.info("Archived {} old appointments for org {}", archived, organization.getId());
                });
            } catch (Exception e) {
                summary.orgsFailed++;
                log.error("Appointment archive failed for org {}", organization.getId(), e);
            }
        }
        TenantContext.clear();

        log.info("Appointment archive run complete: {} orgs processed, {} archived, {} orgs errored",
                summary.orgsProcessed, summary.appointmentsArchived, summary.orgsFailed);
    }

    private static final class RunSummary {
        private int orgsProcessed;
        private int orgsFailed;
        private int appointmentsArchived;
    }
}
