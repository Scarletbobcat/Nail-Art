package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

class ServiceRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from services");
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
        Service saved = TenantContext.runAs(orgA, () -> serviceRepository.save(service("Manicure")));

        Service actual = TenantContext.runAs(orgA, () -> serviceRepository.findScopedById(saved.getId()).orElseThrow());

        assertThat(actual.getName())
                .as("activeContext=%s seedOrgA=%s serviceId=%s", TenantContext.get(), orgA, saved.getId())
                .isEqualTo("Manicure");
        assertThat(actual.isUnavailabilityMarker())
                .as("new API-created services must not become the reserved unavailability marker")
                .isFalse();
    }

    @Test
    void findByNameContainingIgnoreCase_matchesPartialNames() {
        TenantContext.runAs(orgA, () -> {
            serviceRepository.save(service("Gel Manicure"));
            serviceRepository.save(service("Classic Pedicure"));
            serviceRepository.save(service("Waxing"));
        });

        Page<Service> page = TenantContext.runAs(
                orgA,
                () -> serviceRepository.findByNameContainingIgnoreCase("icure", PageRequest.of(0, 20))
        );

        assertThat(names(page.getContent()))
                .as("search=icure activeContext=%s seedOrgA=%s returnedNames=%s",
                        TenantContext.get(), orgA, names(page.getContent()))
                .containsExactlyInAnyOrder("Classic Pedicure", "Gel Manicure");
    }

    @Test
    void pageable_respectsSizeLimit() {
        TenantContext.runAs(orgA, () -> {
            serviceRepository.save(service("Gel Manicure"));
            serviceRepository.save(service("Classic Pedicure"));
            serviceRepository.save(service("Waxing"));
        });

        Page<Service> page = TenantContext.runAs(orgA, () -> serviceRepository.findAll(PageRequest.of(0, 2)));

        assertThat(page.getContent())
                .as("activeContext=%s seedOrgA=%s pageSize=2 returnedNames=%s",
                        TenantContext.get(), orgA, names(page.getContent()))
                .hasSize(2);
        assertThat(page.getTotalElements())
                .as("activeContext=%s seedOrgA=%s", TenantContext.get(), orgA)
                .isEqualTo(3);
    }

    @Test
    void unavailabilityMarker_partialUniqueConstraint_oneTrueAllowed() {
        UUID markerId = insertService(orgA, "Unavailable", true);
        UUID normalId = insertService(orgA, "Gel Manicure", false);

        assertThat(markerFlags(markerId, normalId))
                .as("one marker and one normal service should coexist in one org")
                .containsExactly(true, false);
    }

    @Test
    void unavailabilityMarker_partialUniqueConstraint_secondTrueFails() {
        insertService(orgA, "Unavailable", true);

        assertThatThrownBy(() -> insertService(orgA, "PTO", true))
                .as("only one unavailability marker service is allowed per organization")
                .isInstanceOf(Exception.class);
    }

    @Test
    void unavailabilityMarker_distinctOrgs_eachAllowedOneMarker() {
        UUID orgAMarker = insertService(orgA, "Unavailable", true);
        UUID orgBMarker = insertService(orgB, "Unavailable", true);

        assertThat(markerFlags(orgAMarker, orgBMarker))
                .as("marker uniqueness must be scoped per organization")
                .containsExactly(true, true);
    }

    @Test
    void duplicateName_caseInsensitiveWithinOrg_fails() {
        insertService(orgA, "Gel Manicure", false);

        assertThatThrownBy(() -> insertService(orgA, "gel manicure", false))
                .as("service names are unique case-insensitively inside an organization")
                .isInstanceOf(Exception.class);
    }

    @Test
    void duplicateName_sameNameInDifferentOrgs_succeeds() {
        UUID orgAService = insertService(orgA, "Gel Manicure", false);
        UUID orgBService = insertService(orgB, "gel manicure", false);

        assertThat(orgAService).isNotEqualTo(orgBService);
    }

    @Test
    void tenantId_serviceDiscriminator_filters() {
        UUID orgAServiceId = insertService(orgA, "Gel Manicure", false);
        UUID orgBServiceId = insertService(orgB, "Waxing", false);

        List<Service> services = TenantContext.runAs(orgA, () -> serviceRepository.findAll());

        assertThat(ids(services))
                .as("operation=service select auto-filter activeContext=%s seedOrgA=%s seedOrgB=%s orgAService=%s orgBService=%s returnedIds=%s",
                        TenantContext.get(), orgA, orgB, orgAServiceId, orgBServiceId, ids(services))
                .containsExactly(orgAServiceId);
    }

    @Test
    @Disabled("Hibernate 6.5 @TenantId does not guard EntityManager.find; tenant entities must use scoped JPQL lookups")
    void tenantId_entityManagerFind_respectsDiscriminator() {
        UUID orgBServiceId = insertService(orgB, "Waxing", false);

        Service found = TenantContext.runAs(orgA, () -> entityManager.find(Service.class, orgBServiceId));

        assertThat(found)
                .as("operation=EntityManager.find activeContext=%s seedOrgA=%s seedOrgB=%s targetService=%s targetStoredOrg=%s",
                        TenantContext.get(), orgA, orgB, orgBServiceId, orgB)
                .isNull();
    }

    @Test
    void tenantId_scopedIdQuery_respectsDiscriminator() {
        UUID orgBServiceId = insertService(orgB, "Waxing", false);

        Optional<Service> found = TenantContext.runAs(orgA, () -> serviceRepository.findScopedById(orgBServiceId));

        assertThat(found)
                .as("operation=scoped service id query activeContext=%s seedOrgA=%s seedOrgB=%s targetService=%s targetStoredOrg=%s",
                        TenantContext.get(), orgA, orgB, orgBServiceId, orgB)
                .isEmpty();
    }

    private UUID insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, '+15555550101', 'America/New_York') returning id",
                UUID.class,
                name
        );
    }

    private UUID insertService(UUID organizationId, String name, boolean unavailabilityMarker) {
        return jdbcTemplate.queryForObject(
                "insert into services (organization_id, name, is_unavailability_marker) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                unavailabilityMarker
        );
    }

    private Service service(String name) {
        Service service = new Service();
        service.setName(name);
        return service;
    }

    private List<UUID> ids(List<Service> services) {
        return services.stream().map(Service::getId).toList();
    }

    private List<String> names(List<Service> services) {
        return services.stream().map(Service::getName).toList();
    }

    private List<Boolean> markerFlags(UUID... serviceIds) {
        return List.of(serviceIds).stream()
                .map(id -> jdbcTemplate.queryForObject(
                        "select is_unavailability_marker from services where id = ?",
                        Boolean.class,
                        id
                ))
                .toList();
    }
}
