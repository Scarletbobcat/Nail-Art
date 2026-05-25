package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.ClientRepository;
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
class ClientServiceTest {
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientService clientService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createClient_persistsWithOrgFromPrincipal() {
        UUID organizationId = UUID.randomUUID();
        Client client = client("Anna", "330-555-1234");

        when(clientRepository.save(any())).thenAnswer(invocation -> {
            Client saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setOrganizationId(TenantContext.get());
            return saved;
        });

        Client result = TenantContext.runAs(organizationId, () -> clientService.createClient(client));

        assertThat(result.getOrganizationId()).isEqualTo(organizationId);
        assertThat(result.getName()).isEqualTo("Anna");
        verify(clientRepository).save(client);
    }

    @Test
    void editClient_idFromAnotherOrg_returnsEmpty() {
        UUID attackerOrg = UUID.randomUUID();
        UUID targetClientId = UUID.randomUUID();
        Client patch = client("Mallory", "330-555-9999");

        when(clientRepository.findScopedById(targetClientId)).thenReturn(Optional.empty());

        Optional<Client> result = TenantContext.runAs(
                attackerOrg,
                () -> clientService.editClient(targetClientId, patch)
        );

        assertThat(result).isEmpty();
        verify(clientRepository, never()).save(any());
    }

    @Test
    void createClient_emptyPhoneNumber_delegatesToDatabaseConstraintPolicy() {
        Client client = client("Walk-in", "");

        when(clientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Client result = clientService.createClient(client);

        assertThat(result.getPhoneNumber()).isEmpty();
        verify(clientRepository, never()).findByPhoneNumber(any());
        verify(clientRepository).save(client);
    }

    private Client client(String name, String phone) {
        Client client = new Client();
        client.setName(name);
        client.setPhoneNumber(phone);
        return client;
    }
}
