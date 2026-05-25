package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.dtos.LoginUserDto;
import com.nail_art.appointment_book.responses.LoginResponse;
import com.nail_art.appointment_book.services.AuthenticationService;
import com.nail_art.appointment_book.services.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

    private final JwtService jwtService;

    private final AuthenticationService authenticationService;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> registerDeleted() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDto loginUserDto) {
        User authenticatedUser = authenticationService.authenticate(loginUserDto);

        String refreshToken = authenticationService.generateRefreshToken(authenticatedUser);
        String jwtToken = authenticationService.generateAccessToken(authenticatedUser);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken(jwtToken);
        loginResponse.setExpiresIn(jwtService.getExpirationTime());


        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie(refreshToken, REFRESH_TOKEN_MAX_AGE_SECONDS).toString())
                .body(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req) {
        String refreshToken = findRefreshToken(req);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token is missing");
        }

        try {
            String newAccessToken = authenticationService.refreshAccessToken(refreshToken);
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setToken(newAccessToken);
            loginResponse.setExpiresIn(jwtService.getExpirationTime());
            return ResponseEntity.ok(loginResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        String refreshToken = findRefreshToken(req);

        if (refreshToken != null) {
            authenticationService.deleteRefreshToken(refreshToken);
        }

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie("", 0).toString())
                .build();
    }

    private String findRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie refreshCookie(String value, int maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("None")
                .secure(true)
                .build();
    }
}
