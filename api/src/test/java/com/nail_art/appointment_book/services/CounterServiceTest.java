package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Counter;
import com.nail_art.appointment_book.repositories.CounterRepository;
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
class CounterServiceTest {

    @Mock
    private CounterRepository counterRepository;

    @InjectMocks
    private CounterService counterService;

    @Test
    void incrementsExistingCounter() {
        Counter counter = new Counter(5, "Clients");
        when(counterRepository.findByCollectionName("Clients")).thenReturn(Optional.of(counter));

        long result = counterService.getNextSequence("Clients");

        assertEquals(6L, result);
        verify(counterRepository).save(counter);
        assertEquals(6L, counter.getSequence());
    }

    @Test
    void createsNewCounterWhenNoneExists() {
        when(counterRepository.findByCollectionName("NewCollection")).thenReturn(Optional.empty());

        long result = counterService.getNextSequence("NewCollection");

        assertEquals(1L, result);
        verify(counterRepository).save(any(Counter.class));
    }

    @Test
    void returnsConsecutiveValues() {
        Counter counter = new Counter(0, "Test");
        when(counterRepository.findByCollectionName("Test")).thenReturn(Optional.of(counter));

        assertEquals(1L, counterService.getNextSequence("Test"));
        assertEquals(2L, counterService.getNextSequence("Test"));
        assertEquals(3L, counterService.getNextSequence("Test"));
    }
}
