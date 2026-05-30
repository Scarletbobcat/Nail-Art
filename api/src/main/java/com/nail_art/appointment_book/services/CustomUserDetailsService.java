package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.OrganizationUserRepository;
import com.nail_art.appointment_book.repositories.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final OrganizationUserRepository organizationUserRepository;

    public CustomUserDetailsService(UserRepository userRepository, OrganizationUserRepository organizationUserRepository) {
        this.userRepository = userRepository;
        this.organizationUserRepository = organizationUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String role = organizationUserRepository.findFirstByUserId(user.getId())
                .map(membership -> membership.getRole())
                .orElse("staff");

        UserBuilder builder = org.springframework.security.core.userdetails.User.withUsername(user.getUsername());
        return builder
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }
}
