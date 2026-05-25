package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    private static final ZoneId SALON_TZ = ZoneId.of("America/New_York");
    private UUID employeeId;
    private UUID otherEmployeeId;
    private UUID serviceId;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();
        otherEmployeeId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    void createAppointment_newClientLinking() {
        Appointment appointment = appointment("Jane Doe", "330-555-1234", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(
                employeeId,
                appointment.getEndsAt(),
                appointment.getStartsAt()
        )).thenReturn(List.of());
        when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            client.setId(UUID.randomUUID());
            return client;
        });
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment saved = appointmentService.createAppointment(appointment);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getName())
                .as("phone lookup should create a PostgreSQL client when no scoped client exists phone=%s", appointment.getPhoneNumber())
                .isEqualTo("Jane Doe");
        assertThat(saved.getClientId())
                .as("new appointment should link to the newly created PostgreSQL client")
                .isNotNull();
    }

    @Test
    void editAppointment_sameIdNewTime_succeeds() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = appointment("Jane", "330-555-1234", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        existing.setId(appointmentId);
        Appointment edited = appointment("Jane", "330-555-1234", employeeId, "2026-04-10T10:30:00-04:00", "2026-04-10T11:30:00-04:00");
        when(appointmentRepository.findScopedById(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(employeeId, edited.getEndsAt(), edited.getStartsAt()))
                .thenReturn(List.of(existing));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Appointment> result = appointmentService.editAppointment(appointmentId, edited);

        assertThat(result)
                .as("self-exclusion should ignore appointmentId=%s existing=[%s,%s] edited=[%s,%s]",
                        appointmentId, existing.getStartsAt(), existing.getEndsAt(), edited.getStartsAt(), edited.getEndsAt())
                .isPresent();
    }

    @Test
    void conflict_detection_AE2_overlapDetected() {
        Appointment existing = appointment("Existing", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        Appointment overlapping = appointment("Overlap", "", employeeId, "2026-04-10T10:30:00-04:00", "2026-04-10T11:30:00-04:00");
        Appointment adjacent = appointment("Adjacent", "", employeeId, "2026-04-10T11:00:00-04:00", "2026-04-10T12:00:00-04:00");
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(employeeId, overlapping.getEndsAt(), overlapping.getStartsAt()))
                .thenReturn(List.of(existing));
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(employeeId, adjacent.getEndsAt(), adjacent.getStartsAt()))
                .thenReturn(List.of());
        when(appointmentRepository.save(adjacent)).thenReturn(adjacent);

        assertThatThrownBy(() -> appointmentService.createAppointment(overlapping))
                .as("existing=[%s,%s] candidate=[%s,%s] should overlap",
                        existing.getStartsAt(), existing.getEndsAt(), overlapping.getStartsAt(), overlapping.getEndsAt())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(appointmentService.createAppointment(adjacent))
                .as("existing=[%s,%s] candidate=[%s,%s] exact-touch should be allowed",
                        existing.getStartsAt(), existing.getEndsAt(), adjacent.getStartsAt(), adjacent.getEndsAt())
                .isSameAs(adjacent);
    }

    @Test
    void conflict_detection_sameStartEnd_isConflict() {
        Appointment existing = appointment("Existing", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        Appointment duplicate = appointment("Duplicate", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(employeeId, duplicate.getEndsAt(), duplicate.getStartsAt()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> appointmentService.createAppointment(duplicate))
                .as("existing=[%s,%s] candidate=[%s,%s] identical intervals should conflict",
                        existing.getStartsAt(), existing.getEndsAt(), duplicate.getStartsAt(), duplicate.getEndsAt())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conflict_detection_differentEmployee_noConflict() {
        Appointment candidate = appointment("Different employee", "", otherEmployeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(otherEmployeeId, candidate.getEndsAt(), candidate.getStartsAt()))
                .thenReturn(List.of());
        when(appointmentRepository.save(candidate)).thenReturn(candidate);

        Appointment saved = appointmentService.createAppointment(candidate);

        assertThat(saved)
                .as("employeeId=%s should not conflict with appointments belonging to employeeId=%s",
                        candidate.getEmployeeId(), employeeId)
                .isSameAs(candidate);
    }

    @Test
    void endsAt_lteStartsAt_400FromCheckConstraint() {
        Appointment appointment = appointment("Invalid", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T10:00:00-04:00");
        when(appointmentRepository.save(appointment)).thenThrow(new DataIntegrityViolationException("appointments_ends_after_starts"));

        assertThatThrownBy(() -> appointmentService.createAppointment(appointment))
                .as("startsAt=%s endsAt=%s must be rejected by DB check and mapped by controller",
                        appointment.getStartsAt(), appointment.getEndsAt())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void markReminderSent_doesNotTriggerConflictCheck() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment("Reminder", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        appointment.setId(appointmentId);
        when(appointmentRepository.findScopedById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        appointmentService.markReminderSent(appointmentId);

        verify(appointmentRepository, never()).findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(any(), any(), any());
        assertThat(appointment.getReminderSentAt())
                .as("markReminderSent appointmentId=%s should only set reminderSentAt without conflict check", appointmentId)
                .isNotNull();
    }

    @Test
    void editAppointment_phoneMatchesExistingClient_linksClient() {
        UUID appointmentId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Appointment existing = appointment("Walk-in", "", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        existing.setId(appointmentId);
        Appointment edited = appointment("Typed name", "330-555-1234", employeeId, "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        Client matchedClient = new Client();
        matchedClient.setId(clientId);
        matchedClient.setName("Existing Client");
        matchedClient.setPhoneNumber("330-555-1234");
        when(appointmentRepository.findScopedById(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfter(employeeId, edited.getEndsAt(), edited.getStartsAt()))
                .thenReturn(List.of(existing));
        when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.of(matchedClient));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Appointment> result = appointmentService.editAppointment(appointmentId, edited);

        assertThat(result.orElseThrow().getClientId())
                .as("phone=%s should link to existing scoped clientId=%s", edited.getPhoneNumber(), clientId)
                .isEqualTo(clientId);
        assertThat(result.orElseThrow().getName()).isEqualTo("Existing Client");
    }

    @Test
    void getAppointmentsByDate_AE3_serverInUTC_salonInET_boundaryAt2330ET() {
        LocalDate salonDate = LocalDate.of(2026, 4, 10);
        OffsetDateTime at2330Et = OffsetDateTime.parse("2026-04-10T23:30:00-04:00");
        when(appointmentRepository.findByStartsAtGreaterThanEqualAndStartsAtLessThan(
                OffsetDateTime.parse("2026-04-10T00:00:00-04:00"),
                OffsetDateTime.parse("2026-04-11T00:00:00-04:00")
        )).thenReturn(List.of(appointment("Late", "", employeeId, at2330Et.toString(), "2026-04-11T00:00:00-04:00")));

        List<Appointment> appointments = appointmentService.getAppointmentsByDate(salonDate);

        assertThat(appointments)
                .as("salonTz=%s serverTz=%s inputUtc=%s requestedLocalDate=%s resultLocalDate=%s",
                        SALON_TZ, ZoneId.systemDefault(), at2330Et.toInstant(), salonDate, at2330Et.atZoneSameInstant(SALON_TZ).toLocalDate())
                .hasSize(1);
    }

    @Test
    void getAppointmentsForTomorrow_useSalonTz_notServerTz() {
        LocalDate expectedSalonTomorrow = LocalDate.now(SALON_TZ).plusDays(1);
        when(organizationRepository.currentTimezone()).thenReturn("America/New_York");

        appointmentService.getAppointmentsForTomorrow();

        verify(appointmentRepository).findByStartsAtGreaterThanEqualAndStartsAtLessThan(
                expectedSalonTomorrow.atStartOfDay(SALON_TZ).toOffsetDateTime(),
                expectedSalonTomorrow.plusDays(1).atStartOfDay(SALON_TZ).toOffsetDateTime()
        );
    }

    private Appointment appointment(String name, String phone, UUID employeeId, String startsAt, String endsAt) {
        Appointment appointment = new Appointment();
        appointment.setName(name);
        appointment.setPhoneNumber(phone);
        appointment.setEmployeeId(employeeId);
        appointment.setStartsAt(OffsetDateTime.parse(startsAt));
        appointment.setEndsAt(OffsetDateTime.parse(endsAt));
        appointment.setServiceIds(List.of(serviceId));
        appointment.setShowedUp(false);
        return appointment;
    }
}
