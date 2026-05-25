package com.nail_art.appointment_book.configs;

import com.nail_art.appointment_book.repositories.OrganizationUserRepository;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import com.nail_art.appointment_book.services.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final OrganizationUserRepository organizationUserRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            OrganizationUserRepository organizationUserRepository
    ) {
        this.jwtService = jwtService;
        this.organizationUserRepository = organizationUserRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or malformed bearer token");
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            UUID userId = jwtService.extractUserId(jwt);
            UUID organizationId = jwtService.extractOrganizationId(jwt);
            String role = jwtService.extractRole(jwt);

            if (role == null || role.isBlank()) {
                writeUnauthorized(response, "Invalid bearer token");
                return;
            }

            if (!organizationUserRepository.existsByUserIdAndOrganizationId(userId, organizationId)) {
                writeUnauthorized(response, "Invalid organization membership");
                return;
            }

            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, organizationId, role);
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(role.toUpperCase(Locale.ROOT)))
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            filterChain.doFilter(request, response);
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("Authentication membership check is unavailable");
        } catch (ExpiredJwtException exception) {
            writeUnauthorized(response, "JWT token is expired");
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            writeUnauthorized(response, "Invalid bearer token");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return request.getRequestURI().startsWith("/auth/");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(message);
    }
}
