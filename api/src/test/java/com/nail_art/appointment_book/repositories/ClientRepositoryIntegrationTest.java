package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from clients");
        jdbcTemplate.update("delete from organization_users");
        jdbcTemplate.update("delete from users");
        jdbcTemplate.update("delete from organizations");
        orgA = insertOrganization("Org A");
        orgB = insertOrganization("Org B");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void save_andFindById_roundTrip() {
        Client saved = TenantContext.runAs(orgA, () -> clientRepository.save(client("Anna", "330-555-1234")));

        Client actual = TenantContext.runAs(orgA, () -> clientRepository.findScopedById(saved.getId()).orElseThrow());

        assertThat(actual.getName())
                .as("activeContext=%s seedOrgA=%s clientId=%s", TenantContext.get(), orgA, saved.getId())
                .isEqualTo("Anna");
        assertThat(actual.getPhoneNumber()).isEqualTo("330-555-1234");
    }

    @Test
    void findByNameContainingIgnoreCase_matchesPartialNames() {
        TenantContext.runAs(orgA, () -> {
            clientRepository.save(client("Anna", "330-555-1000"));
            clientRepository.save(client("Joanne", "330-555-1001"));
            clientRepository.save(client("Mia", "330-555-1002"));
        });

        Page<Client> page = TenantContext.runAs(
                orgA,
                () -> clientRepository.findByNameContainingIgnoreCase("ann", PageRequest.of(0, 20))
        );

        assertThat(names(page.getContent()))
                .as("search=ann activeContext=%s seedOrgA=%s returnedNames=%s",
                        TenantContext.get(), orgA, names(page.getContent()))
                .containsExactlyInAnyOrder("Anna", "Joanne");
    }

    @Test
    void pageable_respectsSizeLimit() {
        TenantContext.runAs(orgA, () -> {
            clientRepository.save(client("Anna", "330-555-1000"));
            clientRepository.save(client("Bea", "330-555-1001"));
            clientRepository.save(client("Cora", "330-555-1002"));
        });

        Page<Client> page = TenantContext.runAs(orgA, () -> clientRepository.findAll(PageRequest.of(0, 2)));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void partialUnique_emptyPhone_duplicatesAllowed() {
        UUID first = insertClient(orgA, "Walk-in 1", "");
        UUID second = insertClient(orgA, "Walk-in 2", "");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void partialUnique_nonEmptyDuplicate_fails() {
        insertClient(orgA, "Anna", "330-555-1234");

        assertThatThrownBy(() -> insertClient(orgA, "Bea", "330-555-1234"))
                .as("non-empty phone numbers are unique per organization")
                .isInstanceOf(Exception.class);
    }

    @Test
    void partialUnique_acrossOrgs_allowed() {
        UUID orgAClient = insertClient(orgA, "Anna", "330-555-1234");
        UUID orgBClient = insertClient(orgB, "Bea", "330-555-1234");

        assertThat(orgAClient).isNotEqualTo(orgBClient);
    }

    @Test
    void findByPhoneNumberContaining_dashInputAgainstFormattedStored_noMatch() {
        // Preserves current Mongo regex behavior; if future work normalizes phone storage, update this test.
        insertClient(orgA, "Anna", "(555) 123-4567");

        List<Client> clients = TenantContext.runAs(
                orgA,
                () -> clientRepository.findByPhoneNumberContainingIgnoreCase("555-12")
        );

        assertThat(clients).isEmpty();
    }

    @Test
    void findByPhoneNumberContaining_digitsOnlyInputAgainstFormattedStored_noMatch() {
        // Preserves current Mongo regex behavior; if future work normalizes phone storage, update this test.
        insertClient(orgA, "Anna", "(555) 123-4567");

        List<Client> clients = TenantContext.runAs(
                orgA,
                () -> clientRepository.findByPhoneNumberContainingIgnoreCase("5551")
        );

        assertThat(clients).isEmpty();
    }

    @Test
    void findByPhoneNumberContaining_literalSubstring_match() {
        // Preserves current Mongo regex behavior; if future work normalizes phone storage, update this test.
        UUID clientId = insertClient(orgA, "Anna", "(555) 123-4567");

        List<Client> clients = TenantContext.runAs(
                orgA,
                () -> clientRepository.findByPhoneNumberContainingIgnoreCase("555) 12")
        );

        assertThat(ids(clients)).containsExactly(clientId);
    }

    @Test
    void nameSearch_regexMetaCharBehavesAsLiteralLike() {
        insertClient(orgA, "Anna", "330-555-1234");

        List<Client> clients = TenantContext.runAs(
                orgA,
                () -> clientRepository.findByNameContainingIgnoreCase("a.n")
        );

        assertThat(clients)
                .as("JPA LIKE treats regex metacharacters literally; prior Mongo regex would have matched Anna")
                .isEmpty();
    }

    @Test
    void tenantId_scopedIdQuery_respectsDiscriminator() {
        UUID orgBClientId = insertClient(orgB, "Bea", "330-555-1234");

        Optional<Client> found = TenantContext.runAs(orgA, () -> clientRepository.findScopedById(orgBClientId));

        assertThat(found)
                .as("operation=scoped client id query activeContext=%s seedOrgA=%s seedOrgB=%s targetClient=%s targetStoredOrg=%s",
                        TenantContext.get(), orgA, orgB, orgBClientId, orgB)
                .isEmpty();
    }

    private UUID insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, '+15555550101', 'America/New_York') returning id",
                UUID.class,
                name
        );
    }

    private UUID insertClient(UUID organizationId, String name, String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "insert into clients (organization_id, name, phone_number) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                phoneNumber
        );
    }

    private Client client(String name, String phoneNumber) {
        Client client = new Client();
        client.setName(name);
        client.setPhoneNumber(phoneNumber);
        return client;
    }

    private List<UUID> ids(List<Client> clients) {
        return clients.stream().map(Client::getId).toList();
    }

    private List<String> names(List<Client> clients) {
        return clients.stream().map(Client::getName).toList();
    }
}
