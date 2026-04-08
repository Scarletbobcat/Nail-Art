package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.repositories.ServiceRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private CounterService counterService;

    @InjectMocks
    private ServiceService serviceService;

    private com.nail_art.appointment_book.entities.Service makeService(String name) {
        com.nail_art.appointment_book.entities.Service svc = new com.nail_art.appointment_book.entities.Service();
        svc.setName(name);
        return svc;
    }

    @Nested
    class CreateService {

        @Test
        void createsServiceWithGeneratedId() {
            var svc = makeService("Manicure");
            when(counterService.getNextSequence("Services")).thenReturn(3L);
            when(serviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = serviceService.createService(svc);

            assertEquals(3L, result.getId());
            assertEquals("Manicure", result.getName());
            verify(serviceRepository).save(svc);
        }
    }

    @Nested
    class EditService {

        @Test
        void updatesName() {
            var existing = makeService("Manicure");
            existing.setId(3);

            var updated = makeService("Gel Manicure");
            updated.setId(3);

            when(serviceRepository.findById(3L)).thenReturn(Optional.of(existing));
            when(serviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = serviceService.editService(updated);

            assertNotNull(result);
            assertEquals("Gel Manicure", result.getName());
        }

        @Test
        void returnsNullWhenNotFound() {
            var svc = makeService("Nothing");
            svc.setId(999);
            when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

            assertNull(serviceService.editService(svc));
            verify(serviceRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteService {

        @Test
        void deletesAndReturnsService() {
            var svc = makeService("Pedicure");
            svc.setId(5);

            when(serviceRepository.findById(5L)).thenReturn(Optional.of(svc));

            var result = serviceService.deleteService(svc);

            assertNotNull(result);
            assertEquals("Pedicure", result.getName());
            verify(serviceRepository).delete(svc);
        }

        @Test
        void returnsNullWhenNotFound() {
            var svc = makeService("Nothing");
            svc.setId(999);
            when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

            assertNull(serviceService.deleteService(svc));
            verify(serviceRepository, never()).delete(any());
        }
    }
}
