package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CounterService counterService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ClientService clientService;

    private Client makeClient(String name, String phone) {
        Client client = new Client();
        client.setName(name);
        client.setPhoneNumber(phone);
        return client;
    }

    @Nested
    class CreateClient {

        @Test
        void createsClientWithUniquePhoneNumber() {
            Client client = makeClient("Jane Doe", "330-555-1234");
            when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.empty());
            when(counterService.getNextSequence("Clients")).thenReturn(1L);
            when(clientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Client result = clientService.createClient(client);

            assertEquals(1L, result.getId());
            assertEquals("Jane Doe", result.getName());
            verify(clientRepository).save(client);
        }

        @Test
        void throwsWhenPhoneNumberAlreadyExists() {
            Client existing = makeClient("Existing", "330-555-1234");
            existing.setId(1L);
            Client newClient = makeClient("New", "330-555-1234");

            when(clientRepository.findByPhoneNumber("330-555-1234")).thenReturn(Optional.of(existing));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    clientService.createClient(newClient));

            assertTrue(ex.getMessage().contains("330-555-1234"));
            verify(clientRepository, never()).save(any());
        }

        @Test
        void allowsEmptyPhoneNumberWithoutCheck() {
            Client client = makeClient("Walk-in", "");
            when(counterService.getNextSequence("Clients")).thenReturn(1L);
            when(clientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Client result = clientService.createClient(client);

            assertEquals(1L, result.getId());
            verify(clientRepository, never()).findByPhoneNumber(any());
        }

        @Test
        void allowsNullPhoneNumberWithoutCheck() {
            Client client = makeClient("Walk-in", null);
            when(counterService.getNextSequence("Clients")).thenReturn(1L);
            when(clientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Client result = clientService.createClient(client);

            assertEquals(1L, result.getId());
            verify(clientRepository, never()).findByPhoneNumber(any());
        }
    }

    @Nested
    class EditClient {

        @Test
        void updatesClientAndPropagesToAppointments() {
            Client existing = makeClient("Old Name", "330-555-0000");
            existing.setId(10L);

            Client updated = makeClient("New Name", "330-555-1111");
            updated.setId(10L);

            Appointment appt = new Appointment();
            appt.setId(1);
            appt.setName("Old Name");
            appt.setPhoneNumber("330-555-0000");
            appt.setDate("2026-04-10");
            appt.setStartTime("T10:00");
            appt.setEndTime("T11:00");

            when(clientRepository.findById(10L)).thenReturn(Optional.of(existing));
            when(appointmentRepository.findByClientId(10L)).thenReturn(List.of(appt));
            when(clientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Optional<Client> result = clientService.editClient(updated);

            assertTrue(result.isPresent());
            assertEquals("New Name", result.get().getName());
            assertEquals("330-555-1111", result.get().getPhoneNumber());
            verify(appointmentService).editAppointment(argThat(a ->
                    a.getName().equals("New Name") && a.getPhoneNumber().equals("330-555-1111")));
        }

        @Test
        void returnsEmptyWhenClientNotFound() {
            Client client = makeClient("Test", "330-555-1234");
            client.setId(999L);

            when(clientRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Client> result = clientService.editClient(client);

            assertTrue(result.isEmpty());
            verify(clientRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteClient {

        @Test
        void returnsTrueWhenClientExists() {
            Client client = makeClient("Test", "330-555-1234");
            client.setId(10L);

            when(clientRepository.findById(10L)).thenReturn(Optional.of(client));

            assertTrue(clientService.deleteClient(client));
            verify(clientRepository).delete(client);
        }

        @Test
        void returnsFalseWhenClientNotFound() {
            Client client = makeClient("Test", "330-555-1234");
            client.setId(999L);

            when(clientRepository.findById(999L)).thenReturn(Optional.empty());

            assertFalse(clientService.deleteClient(client));
            verify(clientRepository, never()).delete(any());
        }
    }
}
