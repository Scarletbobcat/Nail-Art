package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import com.posthog.server.PostHogCaptureOptions;
import com.posthog.server.PostHogInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Server-side product analytics - the authoritative source for business events
 * (the browser can lie, drop requests, or be ad-blocked; the API cannot).
 *
 * <p>Actor conservation: events are keyed on the caller's {@code userId}, which is
 * the SAME distinct_id the frontend sets via {@code posthog.identify(me.user.id)}.
 * That makes server events "identified" and merges them onto the same person as the
 * browser's events. Each event also carries {@code organization_id} and
 * {@code organization_name} properties for readable per-salon segmentation.
 *
 * <p>Every method is a no-op when analytics is disabled/unconfigured, and never
 * throws - analytics must not break a user-facing operation.
 */
@Service
public class ProductAnalytics {
    /**
     * Salon/tenant is attached as plain event properties, NOT a PostHog "group" -
     * group analytics is a paid add-on and this project is on the free tier. Filtering
     * and breaking down events by these works fine on the free tier. organization_name
     * is included because a bare UUID is meaningless in dashboards. (To upgrade to a
     * real group later, swap these for options.group("organization", ...).)
     */
    private static final String ORGANIZATION_ID_PROPERTY = "organization_id";
    private static final String ORGANIZATION_NAME_PROPERTY = "organization_name";

    private static final Logger log = LoggerFactory.getLogger(ProductAnalytics.class);

    /** Null when analytics is disabled (tests) or no token is configured (local dev). */
    @Nullable
    private final PostHogInterface postHog;

    private final OrganizationRepository organizationRepository;

    public ProductAnalytics(ObjectProvider<PostHogInterface> postHog, OrganizationRepository organizationRepository) {
        this.postHog = postHog.getIfAvailable();
        this.organizationRepository = organizationRepository;
    }

    /** Capture an event attributed to the current authenticated caller. */
    public void capture(String event) {
        capture(event, Map.of());
    }

    /** Capture an event with properties, attributed to the current authenticated caller. */
    public void capture(String event, Map<String, Object> properties) {
        AuthenticatedPrincipal principal = currentPrincipal();
        if (principal == null) {
            log.debug("No authenticated principal for event '{}' - skipping capture.", event);
            return;
        }
        captureFor(principal.userId(), principal.organizationId(), event, properties);
    }

    /** Capture an identified event for an explicit actor (no person-property updates). */
    public void captureFor(UUID userId, @Nullable UUID organizationId, String event, Map<String, Object> properties) {
        captureFor(userId, organizationId, event, properties, Map.of());
    }

    /**
     * Capture an identified event for an explicit actor - for flows where the
     * principal isn't in the SecurityContext yet (login) or differs from the
     * subject (an admin provisioning a salon).
     *
     * <p>{@code personProperties} are written to the person profile (PostHog's {@code
     * $set}) so people show up by name/email instead of a raw UUID. Set them on login;
     * they persist across all of that person's events, front-end and back-end alike.
     */
    public void captureFor(UUID userId, @Nullable UUID organizationId, String event,
                           Map<String, Object> properties, Map<String, Object> personProperties) {
        if (postHog == null || userId == null) {
            return;
        }
        try {
            var options = PostHogCaptureOptions.builder();
            properties.forEach(options::property);
            personProperties.forEach(options::userProperty);
            if (organizationId != null) {
                options.property(ORGANIZATION_ID_PROPERTY, organizationId.toString());
                String orgName = resolveOrgName(organizationId);
                if (orgName != null) {
                    options.property(ORGANIZATION_NAME_PROPERTY, orgName);
                }
            }
            postHog.capture(userId.toString(), event, options.build());
        } catch (Exception e) {
            log.warn("Failed to capture PostHog event '{}'", event, e);
        }
    }

    /**
     * Capture a personless event for background work with no human actor (the SMS
     * reminder cron, the archive scheduler). {@code $process_person_profile=false}
     * keeps these anonymous - no junk person profiles, and cheaper than identified
     * events. Still carries the organization_id/name properties for per-salon segmentation.
     */
    public void captureSystem(@Nullable UUID organizationId, String event, Map<String, Object> properties) {
        if (postHog == null) {
            return;
        }
        try {
            var options = PostHogCaptureOptions.builder().property("$process_person_profile", false);
            properties.forEach(options::property);
            if (organizationId != null) {
                options.property(ORGANIZATION_ID_PROPERTY, organizationId.toString());
                String orgName = resolveOrgName(organizationId);
                if (orgName != null) {
                    options.property(ORGANIZATION_NAME_PROPERTY, orgName);
                }
            }
            String distinctId = organizationId != null ? organizationId.toString() : "system";
            postHog.capture(distinctId, event, options.build());
        } catch (Exception e) {
            log.warn("Failed to capture PostHog system event '{}'", event, e);
        }
    }

    @Nullable
    private String resolveOrgName(UUID organizationId) {
        // A primary-key lookup per event - negligible at this app's volume, and always
        // fresh, so a renamed salon shows up correctly with no cache to invalidate.
        try {
            return organizationRepository.findById(organizationId)
                    .map(Organization::getName)
                    .filter(name -> !name.isBlank())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal;
        }
        return null;
    }
}
