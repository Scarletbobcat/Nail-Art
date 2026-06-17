package com.nail_art.appointment_book.configs;

import com.posthog.server.PostHog;
import com.posthog.server.PostHogConfig;
import com.posthog.server.PostHogInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the server-side PostHog client. Everything is env-driven (see
 * application.properties) - nothing is hard-coded.
 *
 * <p>The client is optional: with {@code posthog.enabled=false} (tests) no bean
 * is created, and with a blank {@code posthog.api-key} (local dev without a
 * token) the bean is null. Either way {@link
 * com.nail_art.appointment_book.services.ProductAnalytics} no-ops, so the app
 * boots and runs normally without analytics configured.
 */
@Configuration
public class PostHogConfiguration {
    private static final Logger log = LoggerFactory.getLogger(PostHogConfiguration.class);

    /**
     * {@code destroyMethod = "close"} flushes the async batch queue on context
     * shutdown - important on Render, which restarts the process on every deploy.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "posthog", name = "enabled", havingValue = "true", matchIfMissing = true)
    PostHogInterface postHog(
            @Value("${posthog.api-key:}") String apiKey,
            @Value("${posthog.host:https://us.i.posthog.com}") String host) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("posthog.api-key is blank - server-side analytics disabled.");
            return null;
        }
        log.info("Server-side PostHog analytics enabled (host={}).", host);
        return PostHog.with(PostHogConfig.builder(apiKey).host(host).build());
    }
}
