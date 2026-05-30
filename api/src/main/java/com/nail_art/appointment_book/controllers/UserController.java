package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.CreateUserRequest;
import com.nail_art.appointment_book.responses.MeResponse;
import com.nail_art.appointment_book.responses.UserCreateResponse;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import com.nail_art.appointment_book.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/users")
@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> authenticatedUser() {
        return ResponseEntity.ok(userService.getMe(currentPrincipal()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<UserCreateResponse> createUser(@RequestBody CreateUserRequest request) {
        MeResponse.UserSummary user = userService.createUser(request, currentPrincipal());

        return ResponseEntity.status(HttpStatus.CREATED).body(new UserCreateResponse(user));
    }

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BadCredentialsException("User is not authenticated");
        }
        return principal;
    }
}
