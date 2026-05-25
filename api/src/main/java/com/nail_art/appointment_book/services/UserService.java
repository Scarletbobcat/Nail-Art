package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.CreateUserRequest;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationUser;
import com.nail_art.appointment_book.entities.OrganizationUserId;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.repositories.OrganizationUserRepository;
import com.nail_art.appointment_book.repositories.UserRepository;
import com.nail_art.appointment_book.responses.MeResponse;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class UserService {
    private static final Set<String> VALID_ROLES = Set.of("owner", "admin", "staff");

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationUserRepository organizationUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public MeResponse getMe(AuthenticatedPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        Organization organization = organizationRepository.findById(principal.organizationId())
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));

        return new MeResponse(
                new MeResponse.UserSummary(user.getId(), user.getUsername(), principal.role()),
                new MeResponse.OrganizationSummary(
                        organization.getId(),
                        organization.getName(),
                        organization.getTimezone(),
                        organization.getBusinessPhone()
                )
        );
    }

    public MeResponse.UserSummary createUser(CreateUserRequest request, AuthenticatedPrincipal principal) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String username = requireText(request.username(), "username");
        String password = requireText(request.password(), "password");
        String role = normalizedRole(request.role());

        userRepository.findByUsername(username).ifPresent(existing -> {
            throw new DuplicateKeyException("username already exists: " + username);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(password));
        User savedUser = userRepository.save(user);

        OrganizationUser membership = new OrganizationUser();
        membership.setId(new OrganizationUserId(principal.organizationId(), savedUser.getId()));
        membership.setRole(role);
        organizationUserRepository.save(membership);

        return new MeResponse.UserSummary(savedUser.getId(), savedUser.getUsername(), role);
    }

    private String normalizedRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }

        String normalized = role.toLowerCase(Locale.ROOT);
        if (!VALID_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("role must be one of: owner, admin, staff");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
