package com.nail_art.appointment_book.multitenancy;

import java.util.UUID;
import java.util.function.Supplier;

public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T runAs(UUID organizationId, Supplier<T> body) {
        UUID prior = CURRENT.get();
        CURRENT.set(organizationId);

        try {
            return body.get();
        } finally {
            if (prior == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prior);
            }
        }
    }

    public static void runAs(UUID organizationId, Runnable body) {
        runAs(organizationId, () -> {
            body.run();
            return null;
        });
    }
}
