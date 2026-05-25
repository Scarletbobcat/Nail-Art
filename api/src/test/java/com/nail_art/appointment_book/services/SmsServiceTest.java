package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {
    @Mock
    private AppointmentService appointmentService;

    @Mock
    private OrganizationSettingsRepository organizationSettingsRepository;

    @Mock
    private SmsDeliveryGateway smsDeliveryGateway;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void sendReminders_iteratesOrgsWithRunAs() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        List<UUID> contextsSeen = new ArrayList<>();
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation -> {
            contextsSeen.add(TenantContext.get());
            return TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment);
        });
        when(smsDeliveryGateway.sendReminder(anyString(), anyString())).thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        assertThat(contextsSeen)
                .as("orgs processed by scheduler should run under TenantContext orgA=%s orgB=%s", orgA, orgB)
                .containsExactly(orgA, orgB);
        assertThat(TenantContext.get())
                .as("TenantContext must be cleared after scheduler returns")
                .isNull();
        verify(appointmentService).markReminderSent(orgAAppointment.getId());
        verify(appointmentService).markReminderSent(orgBAppointment.getId());
    }

    @Test
    void sendReminders_perOrgTryCatch() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation ->
                TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment)
        );
        doThrow(new RuntimeException("twilio org A outage"))
                .when(smsDeliveryGateway)
                .sendReminder(orgAAppointment.getPhoneNumber(), SmsService.buildReminderMessage(orgAAppointment));
        when(smsDeliveryGateway.sendReminder(orgBAppointment.getPhoneNumber(), SmsService.buildReminderMessage(orgBAppointment)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        verify(appointmentService).markReminderSent(orgBAppointment.getId());
    }

    @Test
    void sendReminders_twilioFailureOnOneAppointment_othersDispatch() {
        UUID orgA = UUID.randomUUID();
        Appointment failed = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment sent = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue()).thenReturn(List.of(settings(orgA)));
        when(appointmentService.getAppointmentsForTomorrow()).thenReturn(List.of(failed, sent));
        doThrow(new RuntimeException("transient send failure"))
                .when(smsDeliveryGateway)
                .sendReminder(failed.getPhoneNumber(), SmsService.buildReminderMessage(failed));
        when(smsDeliveryGateway.sendReminder(sent.getPhoneNumber(), SmsService.buildReminderMessage(sent)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        verify(appointmentService).markReminderSent(sent.getId());
    }

    @Test
    void smsRemindersEnabled_lookupQueriesOrganizationSettings_notOrganizations() {
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue()).thenReturn(List.of());

        smsService().sendReminders();

        verify(organizationSettingsRepository).findBySmsRemindersEnabledTrue();
    }

    private SmsService smsService() {
        return new SmsService(appointmentService, organizationSettingsRepository, smsDeliveryGateway);
    }

    private OrganizationSettings settings(UUID organizationId) {
        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganizationId(organizationId);
        settings.setSmsRemindersEnabled(true);
        return settings;
    }

    private Appointment appointment(UUID appointmentId, String phoneNumber) {
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setName("Jane Doe");
        appointment.setPhoneNumber(phoneNumber);
        appointment.setStartsAt(OffsetDateTime.parse("2026-04-10T10:00:00-04:00"));
        appointment.setEndsAt(OffsetDateTime.parse("2026-04-10T11:00:00-04:00"));
        return appointment;
    }
}
