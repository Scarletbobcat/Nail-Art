package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.LoginUserDto;
import com.nail_art.appointment_book.dtos.RegisterUserDto;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User signup(RegisterUserDto input) {
        if (userRepository.findByUsername(input.getUsername()).isPresent()) {
            throw new RuntimeException("User with username '" + input.getUsername() + "' already exists.");
        }
        User user = new User();
        user.setUsername(input.getUsername());
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        user.setEmail(input.getEmail());

        return userRepository.save(user);
    }

    public User authenticate(LoginUserDto input) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getUsername(),
                        input.getPassword()
                )
        );

        return userRepository.findByUsername(input.getUsername())
                .orElseThrow();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return jwtService.generateRefreshToken(userDetails);
    }

    public boolean validateRefreshToken(String token) {
        return jwtService.validateRefreshToken(token);
    }

    public void deleteRefreshToken(String token) {
        jwtService.deleteRefreshToken(token);
    }
}
