package com.nail_art.appointment_book.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {
    private static final String ORG_A_NAME = "Salon Alpha";
    private static final String ORG_A_PHONE = "330-111-1111";
    private static final String ORG_B_NAME = "Salon Bravo";
    private static final String ORG_B_PHONE = "330-222-2222";

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private OrganizationSettingsRepository organizationSettingsRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SmsDeliveryGateway smsDeliveryGateway;

    @Mock
    private TwilioCredentialsService twilioCredentialsService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(SmsService.class)).addAppender(logAppender);
    }

    @AfterEach
    void cleanUp() {
        ((Logger) LoggerFactory.getLogger(SmsService.class)).detachAppender(logAppender);
        TenantContext.clear();
    }

    @Test
    void sendReminders_iteratesOrgsWithRunAs() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        stubOrganization(orgB, ORG_B_NAME, ORG_B_PHONE);
        stubCompleteCreds(orgA, "orgA");
        stubCompleteCreds(orgB, "orgB");
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        List<UUID> contextsSeen = new ArrayList<>();
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation -> {
            contextsSeen.add(TenantContext.get());
            return TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment);
        });
        when(smsDeliveryGateway.sendReminder(anyString(), anyString(), any(TwilioCredentials.class)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

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
    void sendReminders_sendsEachOrgItsOwnCredentialsAndIdentity_neverAnothers() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        stubOrganization(orgB, ORG_B_NAME, ORG_B_PHONE);
        TwilioCredentials credsA = stubCompleteCreds(orgA, "orgA");
        TwilioCredentials credsB = stubCompleteCreds(orgB, "orgB");
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation ->
                TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment)
        );
        when(smsDeliveryGateway.sendReminder(anyString(), anyString(), any(TwilioCredentials.class)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        // org A's client gets org A's identity AND org A's Twilio account; org B's gets org B's.
        verify(smsDeliveryGateway).sendReminder(
                orgAAppointment.getPhoneNumber(),
                SmsService.buildReminderMessage(orgAAppointment, ORG_A_NAME, ORG_A_PHONE),
                credsA);
        verify(smsDeliveryGateway).sendReminder(
                orgBAppointment.getPhoneNumber(),
                SmsService.buildReminderMessage(orgBAppointment, ORG_B_NAME, ORG_B_PHONE),
                credsB);
        // never org B's credentials for org A's recipient, and vice versa
        verify(smsDeliveryGateway, never())
                .sendReminder(eq(orgAAppointment.getPhoneNumber()), anyString(), eq(credsB));
        verify(smsDeliveryGateway, never())
                .sendReminder(eq(orgBAppointment.getPhoneNumber()), anyString(), eq(credsA));
    }

    @Test
    void buildReminderMessage_carriesPerOrgSalonIdentityAndNeverAnother() {
        Appointment appointment = appointment(UUID.randomUUID(), "330-555-1000");

        String messageA = SmsService.buildReminderMessage(appointment, ORG_A_NAME, ORG_A_PHONE);
        String messageB = SmsService.buildReminderMessage(appointment, ORG_B_NAME, ORG_B_PHONE);

        assertThat(messageA)
                .contains(ORG_A_NAME).contains(ORG_A_PHONE)
                .doesNotContain(ORG_B_NAME).doesNotContain(ORG_B_PHONE)
                .doesNotContain("Nail Art & Spa LLC.").doesNotContain("330-758-6633");
        assertThat(messageB)
                .contains(ORG_B_NAME).contains(ORG_B_PHONE)
                .doesNotContain(ORG_A_NAME).doesNotContain(ORG_A_PHONE);
    }

    @Test
    void sendReminders_perOrgTryCatch() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        stubOrganization(orgB, ORG_B_NAME, ORG_B_PHONE);
        TwilioCredentials credsA = stubCompleteCreds(orgA, "orgA");
        TwilioCredentials credsB = stubCompleteCreds(orgB, "orgB");
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation ->
                TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment)
        );
        doThrow(new RuntimeException("twilio org A outage"))
                .when(smsDeliveryGateway)
                .sendReminder(orgAAppointment.getPhoneNumber(),
                        SmsService.buildReminderMessage(orgAAppointment, ORG_A_NAME, ORG_A_PHONE),
                        credsA);
        when(smsDeliveryGateway.sendReminder(orgBAppointment.getPhoneNumber(),
                SmsService.buildReminderMessage(orgBAppointment, ORG_B_NAME, ORG_B_PHONE), credsB))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        verify(appointmentService).markReminderSent(orgBAppointment.getId());
    }

    @Test
    void sendReminders_twilioFailureOnOneAppointment_othersDispatch() {
        UUID orgA = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        TwilioCredentials credsA = stubCompleteCreds(orgA, "orgA");
        Appointment failed = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment sent = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue()).thenReturn(List.of(settings(orgA)));
        when(appointmentService.getAppointmentsForTomorrow()).thenReturn(List.of(failed, sent));
        doThrow(new RuntimeException("transient send failure"))
                .when(smsDeliveryGateway)
                .sendReminder(failed.getPhoneNumber(),
                        SmsService.buildReminderMessage(failed, ORG_A_NAME, ORG_A_PHONE), credsA);
        when(smsDeliveryGateway.sendReminder(sent.getPhoneNumber(),
                SmsService.buildReminderMessage(sent, ORG_A_NAME, ORG_A_PHONE), credsA))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        verify(appointmentService).markReminderSent(sent.getId());
    }

    @Test
    void sendReminders_incompleteCreds_skipsOrgQuietly_otherOrgsStillSend() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        stubOrganization(orgB, ORG_B_NAME, ORG_B_PHONE);
        // org A has incomplete config (missing token) -> quiet skip; org B is fully configured.
        when(twilioCredentialsService.findForOrganization(orgA))
                .thenReturn(new TwilioCredentials("ACorgA", null, ORG_A_PHONE));
        TwilioCredentials credsB = stubCompleteCreds(orgB, "orgB");
        Appointment orgAAppointment = appointment(UUID.randomUUID(), "330-555-1000");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        lenient().when(appointmentService.getAppointmentsForTomorrow()).thenAnswer(invocation ->
                TenantContext.get().equals(orgA) ? List.of(orgAAppointment) : List.of(orgBAppointment)
        );
        when(smsDeliveryGateway.sendReminder(eq(orgBAppointment.getPhoneNumber()), anyString(), eq(credsB)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        // org A is skipped without ever calling the gateway; org B still sends.
        verify(smsDeliveryGateway, never())
                .sendReminder(eq(orgAAppointment.getPhoneNumber()), anyString(), any());
        verify(appointmentService).markReminderSent(orgBAppointment.getId());
        // A quiet skip is WARN, not ERROR — it must not be conflated with a key-mismatch failure.
        assertThat(logAppender.list)
                .noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void sendReminders_keyMismatch_logsErrorAndSkips_otherOrgsStillSend() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        stubOrganization(orgA, ORG_A_NAME, ORG_A_PHONE);
        stubOrganization(orgB, ORG_B_NAME, ORG_B_PHONE);
        // org A's token was encrypted under a different key -> decrypt RAISES (loud).
        when(twilioCredentialsService.findForOrganization(orgA))
                .thenThrow(new DataAccessResourceFailureException("Wrong key or corrupt data"));
        TwilioCredentials credsB = stubCompleteCreds(orgB, "orgB");
        Appointment orgBAppointment = appointment(UUID.randomUUID(), "330-555-2000");
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue())
                .thenReturn(List.of(settings(orgA), settings(orgB)));
        lenient().when(appointmentService.getAppointmentsForTomorrow()).thenReturn(List.of(orgBAppointment));
        when(smsDeliveryGateway.sendReminder(eq(orgBAppointment.getPhoneNumber()), anyString(), eq(credsB)))
                .thenReturn(SmsDeliveryGateway.Result.SENT);

        smsService().sendReminders();

        // org B still sends despite org A's key failure...
        verify(appointmentService).markReminderSent(orgBAppointment.getId());
        // ...and the key mismatch is LOUD (ERROR), distinct from the quiet incomplete-config skip.
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains(orgA.toString()));
    }

    @Test
    void smsRemindersEnabled_lookupQueriesOrganizationSettings_notOrganizations() {
        when(organizationSettingsRepository.findBySmsRemindersEnabledTrue()).thenReturn(List.of());

        smsService().sendReminders();

        verify(organizationSettingsRepository).findBySmsRemindersEnabledTrue();
    }

    private SmsService smsService() {
        return new SmsService(appointmentService, organizationSettingsRepository, organizationRepository,
                smsDeliveryGateway, twilioCredentialsService);
    }

    private void stubOrganization(UUID organizationId, String name, String businessPhone) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName(name);
        organization.setBusinessPhone(businessPhone);
        lenient().when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
    }

    private TwilioCredentials stubCompleteCreds(UUID organizationId, String tag) {
        TwilioCredentials creds = new TwilioCredentials("AC" + tag, tag + "-token", "+15550000000");
        when(twilioCredentialsService.findForOrganization(organizationId)).thenReturn(creds);
        return creds;
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
