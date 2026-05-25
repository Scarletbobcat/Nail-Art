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

    public static Scope openScope(UUID organizationId) {
        return new Scope(organizationId);
    }

    public static <T> T runAs(UUID organizationId, Supplier<T> body) {
        try (Scope ignored = openScope(organizationId)) {
            return body.get();
        }
    }

    public static void runAs(UUID organizationId, Runnable body) {
        runAs(organizationId, () -> {
            body.run();
            return null;
        });
    }

    public static final class Scope implements AutoCloseable {
        private final UUID prior;

        private Scope(UUID organizationId) {
            this.prior = CURRENT.get();
            CURRENT.set(organizationId);
        }

        @Override
        public void close() {
            if (prior == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prior);
            }
        }
    }
}
