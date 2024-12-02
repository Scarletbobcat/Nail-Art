package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Counter;
import com.nail_art.appointment_book.repositories.CounterRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class CounterService {
    private final CounterRepository counterRepository;

    private static final Logger logger = LoggerFactory.getLogger(CounterService.class);

    public CounterService(CounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    public long getNextSequence(String sequenceName) {
        Optional<Counter> optionalCounter = counterRepository.findByCollectionName(sequenceName);
        Counter counter;
        if (optionalCounter.isPresent()) {
            counter = optionalCounter.get();
            logger.info("Found existing counter: {}", counter);
        } else {
            counter = new Counter(0, sequenceName);
            logger.info("Creating new counter: {}", counter);
        }
        counter.setSequence(counter.getSequence() + 1);
        counterRepository.save(counter);
        return counter.getSequence();
    }
}
