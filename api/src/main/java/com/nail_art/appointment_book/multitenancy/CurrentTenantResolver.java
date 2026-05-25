package com.nail_art.appointment_book.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentTenantResolver implements CurrentTenantIdentifierResolver<UUID> {
    public static final UUID UNSET_TENANT_SENTINEL = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID currentTenant = TenantContext.get();
        return currentTenant == null ? UNSET_TENANT_SENTINEL : currentTenant;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
