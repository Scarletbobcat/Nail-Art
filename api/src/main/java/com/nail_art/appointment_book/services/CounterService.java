package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Counter;
import com.nail_art.appointment_book.repositories.CounterRepository;
import org.springframework.stereotype.Service;

@Service
public class CounterService {
    private final CounterRepository counterRepository;

    public CounterService(CounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    public long getNextSequence(String sequenceName) {
        Counter counter = counterRepository.findByCollectionName(sequenceName).orElse(new Counter(0, sequenceName));
        counter.setSequence(counter.getSequence() + 1);
        counterRepository.save(counter);
        return counter.getSequence();
    }
}
