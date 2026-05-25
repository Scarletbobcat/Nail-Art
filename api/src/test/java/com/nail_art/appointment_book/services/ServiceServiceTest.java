package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {
    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceService serviceService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createService_persistsWithOrgFromPrincipal() {
        UUID organizationId = UUID.randomUUID();
        Service service = service("Manicure");

        when(serviceRepository.save(any())).thenAnswer(invocation -> {
            Service saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setOrganizationId(TenantContext.get());
            return saved;
        });

        Service result = TenantContext.runAs(organizationId, () -> serviceService.createService(service));

        assertThat(result.getOrganizationId())
                .as("activeContext=%s expectedOrg=%s serviceId=%s",
                        TenantContext.get(), organizationId, result.getId())
                .isEqualTo(organizationId);
        assertThat(result.getName()).isEqualTo("Manicure");
        assertThat(result.isUnavailabilityMarker()).isFalse();
        verify(serviceRepository).save(service);
    }

    @Test
    void editService_idFromAnotherOrg_returnsEmpty() {
        UUID attackerOrg = UUID.randomUUID();
        UUID targetServiceId = UUID.randomUUID();
        Service patch = service("Mallory");

        when(serviceRepository.findScopedById(targetServiceId)).thenReturn(Optional.empty());

        Optional<Service> result = TenantContext.runAs(
                attackerOrg,
                () -> serviceService.editService(targetServiceId, patch)
        );

        assertThat(result)
                .as("operation=service cross-org edit activeContext=%s attackerOrg=%s targetService=%s",
                        TenantContext.get(), attackerOrg, targetServiceId)
                .isEmpty();
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createService_isUnavailabilityMarkerInBody_ignored() {
        Service request = service("Unavailable");
        request.setUnavailabilityMarker(true);

        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Service result = serviceService.createService(request);

        assertThat(result.isUnavailabilityMarker())
                .as("public create API must not allow callers to mint the reserved marker row")
                .isFalse();
    }

    @Test
    void editService_attemptToToggleMarkerFalse_leavesExistingFlagTrue() {
        UUID markerId = UUID.randomUUID();
        Service existing = service("Unavailable");
        existing.setId(markerId);
        existing.setUnavailabilityMarker(true);
        Service patch = service("PTO");
        patch.setUnavailabilityMarker(false);

        when(serviceRepository.findScopedById(markerId)).thenReturn(Optional.of(existing));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Service> result = serviceService.editService(markerId, patch);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("PTO");
        assertThat(result.get().isUnavailabilityMarker())
                .as("edit should rename the marker row without making marker ownership writable through the API")
                .isTrue();
    }

    private Service service(String name) {
        Service service = new Service();
        service.setName(name);
        return service;
    }
}
