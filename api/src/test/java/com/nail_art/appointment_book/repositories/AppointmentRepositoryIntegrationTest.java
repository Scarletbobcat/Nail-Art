package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.AppointmentServiceLink;
import com.nail_art.appointment_book.entities.AppointmentServiceLinkId;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentServiceLinkRepository appointmentServiceLinkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID orgA;
    private UUID orgB;
    private UUID orgAEmployee;
    private UUID orgAService;
    private UUID orgBService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from clients");
        jdbcTemplate.update("delete from services");
        jdbcTemplate.update("delete from employees");
        jdbcTemplate.update("delete from organization_users");
        jdbcTemplate.update("delete from users");
        jdbcTemplate.update("delete from organizations");
        orgA = insertOrganization("Org A");
        orgB = insertOrganization("Org B");
        orgAEmployee = insertEmployee(orgA, "Anna");
        orgAService = insertService(orgA, "Gel Manicure");
        orgBService = insertService(orgB, "Waxing");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void findByStartsAtBetween_inclusiveExclusiveRange() {
        UUID included = insertAppointment(orgA, orgAEmployee, "Included", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", null);
        insertAppointment(orgA, orgAEmployee, "Excluded", "2026-04-11T00:00:00-04:00", "2026-04-11T01:00:00-04:00", null);

        List<Appointment> appointments = TenantContext.runAs(
                orgA,
                () -> appointmentRepository.findByStartsAtGreaterThanEqualAndStartsAtLessThan(
                        OffsetDateTime.parse("2026-04-10T00:00:00-04:00"),
                        OffsetDateTime.parse("2026-04-11T00:00:00-04:00")
                )
        );

        assertThat(ids(appointments))
                .as("range=[%s,%s) activeContext=%s orgA=%s included=%s",
                        "2026-04-10T00:00-04:00", "2026-04-11T00:00-04:00", TenantContext.get(), orgA, included)
                .containsExactly(included);
    }

    @Test
    void findByStartsAtBetween_excludesArchived() {
        UUID archived = insertAppointment(orgA, orgAEmployee, "Archived", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", "2026-04-09T12:00:00-04:00");
        insertAppointment(orgA, orgAEmployee, "Active", "2026-04-10T12:00:00-04:00", "2026-04-10T13:00:00-04:00", null);

        List<Appointment> appointments = TenantContext.runAs(
                orgA,
                () -> appointmentRepository.findByStartsAtGreaterThanEqualAndStartsAtLessThan(
                        OffsetDateTime.parse("2026-04-10T00:00:00-04:00"),
                        OffsetDateTime.parse("2026-04-11T00:00:00-04:00")
                )
        );

        assertThat(ids(appointments))
                .as("archived appointmentId=%s activeContext=%s should be hidden from default date queries", archived, TenantContext.get())
                .doesNotContain(archived);
    }

    @Test
    void tenantId_appointmentDiscriminator() {
        UUID orgBAppointment = insertAppointment(orgB, insertEmployee(orgB, "Bea"), "Org B", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", null);

        Optional<Appointment> scoped = TenantContext.runAs(orgA, () -> appointmentRepository.findScopedById(orgBAppointment));
        Appointment entityFind = TenantContext.runAs(orgA, () -> entityManager.find(Appointment.class, orgBAppointment));

        assertThat(scoped)
                .as("operation=scoped lookup activeContext=%s orgA=%s orgB=%s target=%s", TenantContext.get(), orgA, orgB, orgBAppointment)
                .isEmpty();
        assertThat(entityFind)
                .as("EntityManager.find must not be used as tenant boundary activeContext=%s orgA=%s orgB=%s target=%s",
                        TenantContext.get(), orgA, orgB, orgBAppointment)
                .isNull();
    }

    @Test
    void tenantId_appointmentServiceLink_embeddedIdDiscriminator() {
        UUID appointmentId = insertAppointment(orgA, orgAEmployee, "Org A", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", null);
        insertAppointmentService(orgA, appointmentId, orgAService);
        AppointmentServiceLinkId linkId = new AppointmentServiceLinkId(appointmentId, orgAService);

        Optional<AppointmentServiceLink> orgBLookup = TenantContext.runAs(orgB, () -> appointmentServiceLinkRepository.findById(linkId));

        assertThat(orgBLookup)
                .as("activeContext=%s seedOrgA=%s seedOrgB=%s linkAppointment=%s linkService=%s",
                        TenantContext.get(), orgA, orgB, appointmentId, orgAService)
                .isEmpty();
    }

    @Test
    void appointmentServiceLink_compositeFkBlocksCrossOrgLink() {
        UUID appointmentId = insertAppointment(orgA, orgAEmployee, "Org A", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", null);

        assertThatThrownBy(() -> insertAppointmentService(orgA, appointmentId, orgBService))
                .as("appointmentOrg=%s appointmentId=%s serviceOrg=%s serviceId=%s must be blocked by composite FK",
                        orgA, appointmentId, orgB, orgBService)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, '+15555550101', 'America/New_York') returning id",
                UUID.class,
                name
        );
    }

    private UUID insertEmployee(UUID organizationId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color) values (?, ?, '#111111') returning id",
                UUID.class,
                organizationId,
                name
        );
    }

    private UUID insertService(UUID organizationId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into services (organization_id, name) values (?, ?) returning id",
                UUID.class,
                organizationId,
                name
        );
    }

    private UUID insertAppointment(UUID organizationId, UUID employeeId, String customerName, String startsAt, String endsAt, String archivedAt) {
        return jdbcTemplate.queryForObject(
                """
                        insert into appointments (organization_id, employee_id, customer_name, starts_at, ends_at, phone_number, archived_at)
                        values (?, ?, ?, ?::timestamptz, ?::timestamptz, '330-555-1234', ?::timestamptz)
                        returning id
                        """,
                UUID.class,
                organizationId,
                employeeId,
                customerName,
                startsAt,
                endsAt,
                archivedAt
        );
    }

    private void insertAppointmentService(UUID organizationId, UUID appointmentId, UUID serviceId) {
        jdbcTemplate.update(
                "insert into appointment_services (organization_id, appointment_id, service_id) values (?, ?, ?)",
                organizationId,
                appointmentId,
                serviceId
        );
    }

    private List<UUID> ids(List<Appointment> appointments) {
        return appointments.stream().map(Appointment::getId).toList();
    }
}
