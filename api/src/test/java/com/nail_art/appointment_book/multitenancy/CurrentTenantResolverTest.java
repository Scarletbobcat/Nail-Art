package com.nail_art.appointment_book.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentTenantResolverTest {
    private static final UUID SENTINEL_TENANT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void resolveCurrentTenant_contextUnset_returnsMaxUuidSentinel() {
        CurrentTenantResolver resolver = new CurrentTenantResolver();

        UUID actual = resolver.resolveCurrentTenantIdentifier();

        assertThat(actual)
                .as("Expected sentinel %s but resolver returned %s while TenantContext was %s",
                        SENTINEL_TENANT_ID, actual, TenantContext.get())
                .isEqualTo(SENTINEL_TENANT_ID);
    }

    @Test
    void resolveCurrentTenant_contextSet_returnsThatUuid() {
        UUID orgA = UUID.randomUUID();
        CurrentTenantResolver resolver = new CurrentTenantResolver();

        TenantContext.runAs(orgA, () -> {
            UUID actual = resolver.resolveCurrentTenantIdentifier();

            assertThat(actual)
                    .as("TenantContext was %s while resolver returned %s", TenantContext.get(), actual)
                    .isEqualTo(orgA);
        });
    }

    @Test
    void runAs_nestedInvocation_savesAndRestores() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        TenantContext.runAs(orgA, () -> {
            TenantContext.runAs(orgB, () ->
                    assertThat(TenantContext.get())
                            .as("Inner TenantContext should be %s", orgB)
                            .isEqualTo(orgB)
            );

            assertThat(TenantContext.get())
                    .as("Outer TenantContext should be restored to %s after nested runAs", orgA)
                    .isEqualTo(orgA);
        });

        assertThat(TenantContext.get())
                .as("Top-level TenantContext should restore to null after runAs")
                .isNull();
    }

    @Test
    void runAs_throwingBody_restoresContext() {
        UUID orgA = UUID.randomUUID();

        assertThatThrownBy(() ->
                TenantContext.runAs(orgA, () -> {
                    throw new IllegalStateException("boom");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.get())
                .as("TenantContext should restore to null after throwing runAs body; org was %s", orgA)
                .isNull();
    }

    @Test
    void runAs_threadPoolReuse_doesNotLeakContext() throws Exception {
        UUID orgA = UUID.randomUUID();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> first = executor.submit(() ->
                    assertThatThrownBy(() ->
                            TenantContext.runAs(orgA, () -> {
                                throw new IllegalStateException("boom");
                            })
                    ).isInstanceOf(IllegalStateException.class)
            );
            first.get();

            Future<UUID> second = executor.submit(TenantContext::get);

            assertThat(second.get())
                    .as("TenantContext leaked across reused executor thread after org %s", orgA)
                    .isNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
