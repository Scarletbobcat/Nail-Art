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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private static final Set<String> VALID_ROLES = Set.of("owner", "staff");

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

        // Platform admins are org-less: no role, no organization summary.
        if (principal.platformAdmin()) {
            return new MeResponse(
                    new MeResponse.UserSummary(user.getId(), user.getUsername(), null, true),
                    null
            );
        }

        Organization organization = organizationRepository.findById(principal.organizationId())
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));

        return new MeResponse(
                new MeResponse.UserSummary(user.getId(), user.getUsername(), principal.role(), false),
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

        User savedUser = createUserInOrganization(principal.organizationId(), username, request.email(), password, role);
        return new MeResponse.UserSummary(savedUser.getId(), savedUser.getUsername(), role, false);
    }

    /**
     * Create a user and its membership in a given org. Shared by the owner-facing
     * createUser (scoped to the caller's org) and the platform-admin create-salon
     * flow (provisioning a brand-new org's first owner). Inputs are assumed
     * validated; the duplicate-username check throws so the caller's transaction
     * rolls back cleanly.
     */
    @Transactional
    public User createUserInOrganization(UUID organizationId, String username, String email, String rawPassword, String role) {
        userRepository.findByUsername(username).ifPresent(existing -> {
            throw new DataIntegrityViolationException("username already exists: " + username);
        });

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        User savedUser = userRepository.save(user);

        OrganizationUser membership = new OrganizationUser();
        membership.setId(new OrganizationUserId(organizationId, savedUser.getId()));
        membership.setRole(role);
        organizationUserRepository.save(membership);

        return savedUser;
    }

    private String normalizedRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }

        String normalized = role.toLowerCase(Locale.ROOT);
        if (!VALID_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("role must be one of: owner, staff");
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
