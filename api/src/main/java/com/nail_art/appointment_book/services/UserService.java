package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public List<User> allUsers() {
        List<User> users = userRepository.findAll();
        return users;
    }
}
