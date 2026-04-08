package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.LoginUserDto;
import com.nail_art.appointment_book.dtos.RegisterUserDto;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Nested
    class Signup {

        @Test
        void createsNewUser() {
            RegisterUserDto dto = new RegisterUserDto();
            dto.setUsername("admin");
            dto.setPassword("pass123");
            dto.setEmail("admin@test.com");

            when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("pass123")).thenReturn("encoded_pass");
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            User result = authenticationService.signup(dto);

            assertEquals("admin", result.getUsername());
            assertEquals("encoded_pass", result.getPassword());
            assertEquals("admin@test.com", result.getEmail());
        }

        @Test
        void throwsWhenUsernameAlreadyExists() {
            RegisterUserDto dto = new RegisterUserDto();
            dto.setUsername("admin");
            dto.setPassword("pass123");

            User existing = new User();
            existing.setUsername("admin");
            when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(existing));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    authenticationService.signup(dto));

            assertTrue(ex.getMessage().contains("admin"));
            verify(userRepository, never()).save(any());
        }

        @Test
        void encodesPasswordBeforeSaving() {
            RegisterUserDto dto = new RegisterUserDto();
            dto.setUsername("user");
            dto.setPassword("plaintext");

            when(userRepository.findByUsernameIgnoreCase("user")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("plaintext")).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            User result = authenticationService.signup(dto);

            assertEquals("$2a$hashed", result.getPassword());
            verify(passwordEncoder).encode("plaintext");
        }
    }

    @Nested
    class Authenticate {

        @Test
        void returnsUserOnValidCredentials() {
            LoginUserDto dto = new LoginUserDto();
            dto.setUsername("admin");
            dto.setPassword("pass123");

            User user = new User();
            user.setUsername("admin");

            when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

            User result = authenticationService.authenticate(dto);

            assertEquals("admin", result.getUsername());
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        }

        @Test
        void throwsOnInvalidCredentials() {
            LoginUserDto dto = new LoginUserDto();
            dto.setUsername("admin");
            dto.setPassword("wrong");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class, () ->
                    authenticationService.authenticate(dto));
        }
    }

    @Nested
    class RefreshTokens {

        @Test
        void delegatesGenerateToJwtService() {
            User user = new User();
            user.setUsername("admin");

            when(jwtService.generateRefreshToken(user)).thenReturn("refresh_token_123");

            String token = authenticationService.generateRefreshToken(user);

            assertEquals("refresh_token_123", token);
            verify(jwtService).generateRefreshToken(user);
        }

        @Test
        void delegatesValidateToJwtService() {
            when(jwtService.validateRefreshToken("some_token")).thenReturn(true);

            assertTrue(authenticationService.validateRefreshToken("some_token"));
        }

        @Test
        void delegatesDeleteToJwtService() {
            authenticationService.deleteRefreshToken("some_token");

            verify(jwtService).deleteRefreshToken("some_token");
        }
    }
}
