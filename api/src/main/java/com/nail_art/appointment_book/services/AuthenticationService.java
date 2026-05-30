package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.LoginUserDto;
import com.nail_art.appointment_book.entities.OrganizationUser;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.OrganizationUserRepository;
import com.nail_art.appointment_book.repositories.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrganizationUserRepository organizationUserRepository;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OrganizationUserRepository organizationUserRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.organizationUserRepository = organizationUserRepository;
    }

    public User authenticate(LoginUserDto input) {
        User user = userRepository.findByUsername(input.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        if (!passwordEncoder.matches(input.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Bad credentials");
        }

        return withoutPasswordHash(user);
    }

    public OrganizationUser getPrimaryMembership(User user) {
        return organizationUserRepository.findFirstByUserId(user.getId())
                .orElseThrow(() -> new BadCredentialsException("No organization membership found"));
    }

    public String generateAccessToken(User user) {
        if (user.isPlatformAdmin()) {
            return jwtService.generateAdminToken(user);
        }
        OrganizationUser membership = getPrimaryMembership(user);
        return jwtService.generateToken(user, membership.getOrganizationId(), membership.getRole());
    }

    public String generateRefreshToken(User user) {
        if (user.isPlatformAdmin()) {
            return jwtService.generateAdminRefreshToken(user);
        }
        OrganizationUser membership = getPrimaryMembership(user);
        return jwtService.generateRefreshToken(user, membership.getOrganizationId(), membership.getRole());
    }

    public String refreshAccessToken(String refreshToken) {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Platform admins are org-less: re-check the live flag (it may have been
        // revoked since the refresh token was minted) and mint a fresh admin token.
        if (jwtService.extractIsPlatformAdmin(refreshToken)) {
            if (!user.isPlatformAdmin()) {
                throw new BadCredentialsException("Platform admin access revoked");
            }
            return jwtService.generateAdminToken(user);
        }

        UUID organizationId = jwtService.extractOrganizationId(refreshToken);
        OrganizationUser membership = organizationUserRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new BadCredentialsException("Organization membership not found"));

        return jwtService.generateToken(user, organizationId, membership.getRole());
    }

    public boolean validateRefreshToken(String token) {
        return jwtService.validateRefreshToken(token);
    }

    public void deleteRefreshToken(String token) {
        jwtService.deleteRefreshToken(token);
    }

    private User withoutPasswordHash(User user) {
        User sanitized = new User();
        sanitized.setId(user.getId());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmail(user.getEmail());
        sanitized.setCreatedAt(user.getCreatedAt());
        sanitized.setUpdatedAt(user.getUpdatedAt());
        sanitized.setPlatformAdmin(user.isPlatformAdmin());
        return sanitized;
    }
}
