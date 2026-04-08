package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CounterService counterService;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private Appointment makeAppointment(String name, String phone, String date, String start, String end) {
        Appointment appt = new Appointment();
        appt.setName(name);
        appt.setPhoneNumber(phone);
        appt.setDate(date);
        appt.setStartTime(start);
        appt.setEndTime(end);
        appt.setEmployeeId(1);
        appt.setServices(List.of(1));
        appt.setReminderSent(false);
        appt.setShowedUp(false);
        return appt;
    }

    @Nested
    class CreateAppointment {

        @Test
        void createsNewClientWhenPhoneProvidedAndNoClientExists() {
            Appointment appt = makeAppointment("Jane Doe", "330-555-1234", "2026-04-10", "T10:00", "T11:00");
            when(counterService.getNextSequence("Appointments")).thenReturn(100L);
            when(counterService.getNextSequence("Clients")).thenReturn(50L);
            when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.empty());
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Appointment result = appointmentService.createAppointment(appt);

            assertEquals(50L, result.getClientId());
            ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
            verify(clientRepository).save(clientCaptor.capture());
            Client savedClient = clientCaptor.getValue();
            assertEquals("Jane Doe", savedClient.getName());
            assertEquals("330-555-1234", savedClient.getPhoneNumber());
            assertEquals(50L, savedClient.getId());
        }

        @Test
        void linksExistingClientWhenPhoneMatches() {
            Appointment appt = makeAppointment("Jane", "330-555-1234", "2026-04-10", "T10:00", "T11:00");
            Client existing = new Client();
            existing.setId(25L);
            existing.setName("Jane Doe");
            existing.setPhoneNumber("330-555-1234");

            when(counterService.getNextSequence("Appointments")).thenReturn(100L);
            when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.of(existing));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Appointment result = appointmentService.createAppointment(appt);

            assertEquals(25L, result.getClientId());
            assertEquals("Jane Doe", result.getName());
            verify(clientRepository, never()).save(any());
        }

        @Test
        void skipsClientLinkingWhenPhoneIsEmpty() {
            Appointment appt = makeAppointment("Walk-in", "", "2026-04-10", "T10:00", "T11:00");
            when(counterService.getNextSequence("Appointments")).thenReturn(100L);
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Appointment result = appointmentService.createAppointment(appt);

            assertNull(result.getClientId());
            verify(clientRepository, never()).findByPhoneNumber(any());
        }

        @Test
        void setsIdAndReminderSent() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            when(counterService.getNextSequence("Appointments")).thenReturn(42L);
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Appointment result = appointmentService.createAppointment(appt);

            assertEquals(42L, result.getId());
            assertFalse(result.getReminderSent());
        }

        @Test
        void throwsWhenEndTimeBeforeStartTime() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T14:00", "T10:00");

            assertThrows(IllegalArgumentException.class, () ->
                    appointmentService.createAppointment(appt));
        }

        @Test
        void throwsWhenEndTimeEqualsStartTime() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T10:00", "T10:00");

            assertThrows(IllegalArgumentException.class, () ->
                    appointmentService.createAppointment(appt));
        }

        @Test
        void normalizesUnpaddedTimes() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T9:00", "T10:00");
            when(counterService.getNextSequence("Appointments")).thenReturn(1L);
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Appointment result = appointmentService.createAppointment(appt);

            assertEquals("T09:00", result.getStartTime());
            assertEquals("T10:00", result.getEndTime());
        }

        @Test
        void throwsOnNullTime() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", null, "T10:00");

            assertThrows(IllegalArgumentException.class, () ->
                    appointmentService.createAppointment(appt));
        }

        @Test
        void usesClientsCounterNotAppointmentsForNewClient() {
            Appointment appt = makeAppointment("New Person", "555-123-4567", "2026-04-10", "T10:00", "T11:00");
            when(counterService.getNextSequence("Appointments")).thenReturn(100L);
            when(counterService.getNextSequence("Clients")).thenReturn(50L);
            when(clientRepository.findByPhoneNumber("555-123-4567")).thenReturn(Optional.empty());
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.createAppointment(appt);

            // Called once for "Appointments" (appt id) and once for "Clients" (client id)
            verify(counterService).getNextSequence("Appointments");
            verify(counterService).getNextSequence("Clients");
            verify(counterService, times(2)).getNextSequence(anyString());
        }
    }

    @Nested
    class EditAppointment {

        @Test
        void linksClientWhenPhoneAddedToClientlessAppointment() {
            Appointment existing = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            existing.setId(1);
            existing.setReminderSent(false);

            Appointment edited = makeAppointment("Test", "330-555-9999", "2026-04-10", "T10:00", "T11:00");
            edited.setId(1);
            edited.setClientId(null);
            edited.setReminderSent(false);

            Client client = new Client();
            client.setId(25L);
            client.setName("Test");
            client.setPhoneNumber("330-555-9999");

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(clientRepository.findByPhoneNumber("330-555-9999")).thenReturn(Optional.of(client));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Optional<Appointment> result = appointmentService.editAppointment(edited);

            assertTrue(result.isPresent());
            assertEquals(25L, result.get().getClientId());
        }

        @Test
        void doesNotLinkClientWhenPhoneIsEmpty() {
            Appointment existing = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            existing.setId(1);
            existing.setReminderSent(false);

            Appointment edited = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            edited.setId(1);
            edited.setClientId(null);
            edited.setReminderSent(false);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Optional<Appointment> result = appointmentService.editAppointment(edited);

            assertTrue(result.isPresent());
            assertNull(result.get().getClientId());
            verify(clientRepository, never()).findByPhoneNumber(any());
        }

        @Test
        void returnsEmptyWhenAppointmentNotFound() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            appt.setId(999);
            when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Appointment> result = appointmentService.editAppointment(appt);

            assertTrue(result.isEmpty());
        }

        @Test
        void throwsWhenEndTimeBeforeStartTime() {
            Appointment appt = makeAppointment("Test", "", "2026-04-10", "T14:00", "T10:00");
            appt.setId(1);

            assertThrows(IllegalArgumentException.class, () ->
                    appointmentService.editAppointment(appt));
        }

        @Test
        void resetsReminderWhenStartTimeChanges() {
            Appointment existing = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            existing.setId(1);
            existing.setReminderSent(true);

            Appointment edited = makeAppointment("Test", "", "2026-04-10", "T11:00", "T12:00");
            edited.setId(1);
            edited.setReminderSent(true);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.editAppointment(edited);

            assertFalse(edited.getReminderSent());
        }

        @Test
        void resetsReminderWhenDateChanges() {
            Appointment existing = makeAppointment("Test", "", "2026-04-10", "T10:00", "T11:00");
            existing.setId(1);
            existing.setReminderSent(true);

            Appointment edited = makeAppointment("Test", "", "2026-04-11", "T10:00", "T11:00");
            edited.setId(1);
            edited.setReminderSent(true);

            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.editAppointment(edited);

            assertFalse(edited.getReminderSent());
        }
    }

    @Nested
    class DeleteAppointment {

        @Test
        void returnsTrueWhenAppointmentExists() {
            Appointment appt = new Appointment();
            appt.setId(1);
            when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

            assertTrue(appointmentService.deleteAppointment(appt));
            verify(appointmentRepository).delete(appt);
        }

        @Test
        void returnsFalseWhenAppointmentNotFound() {
            Appointment appt = new Appointment();
            appt.setId(999);
            when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertFalse(appointmentService.deleteAppointment(appt));
            verify(appointmentRepository, never()).delete(any());
        }
    }
}
