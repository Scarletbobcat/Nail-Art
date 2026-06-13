package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentArchiveServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-12T14:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void archiveOldAppointments_uses30DayCutoffForEachOrg() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        when(organizationRepository.findAll()).thenReturn(List.of(organization(orgA), organization(orgB)));
        AppointmentArchiveService service = archiveService();

        service.archiveOldAppointments();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(appointmentRepository).archiveEndedBefore(eq(orgA), cutoffCaptor.capture());
        verify(appointmentRepository).archiveEndedBefore(eq(orgB), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getAllValues())
                .as("archive job should use one stable cutoff across the whole run")
                .containsExactly(
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30),
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30)
                );
        assertThat(TenantContext.get())
                .as("scheduled jobs must not leak tenant context after they finish")
                .isNull();
    }

    @Test
    void archiveOldAppointments_oneOrgFailureDoesNotStopOthers() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        when(organizationRepository.findAll()).thenReturn(List.of(organization(orgA), organization(orgB)));
        when(appointmentRepository.archiveEndedBefore(orgA, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30)))
                .thenThrow(new RuntimeException("database hiccup"));
        AppointmentArchiveService service = archiveService();

        service.archiveOldAppointments();

        verify(appointmentRepository).archiveEndedBefore(orgB, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30));
        assertThat(TenantContext.get())
                .as("failed org should not leak tenant context into the next scheduler run")
                .isNull();
    }

    private AppointmentArchiveService archiveService() {
        return new AppointmentArchiveService(
                appointmentRepository,
                organizationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName("Archive Test");
        return organization;
    }
}
