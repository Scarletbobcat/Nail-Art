package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private EmployeeRepository employeeRepository;

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
        jdbcTemplate.update("delete from employees");
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
        Employee saved = TenantContext.runAs(orgA, () -> employeeRepository.save(employee("Alice", "#ff0000")));

        Employee actual = TenantContext.runAs(orgA, () -> employeeRepository.findById(saved.getId()).orElseThrow());

        assertThat(actual.getName())
                .as("activeContext=%s seedOrgA=%s employeeId=%s", TenantContext.get(), orgA, saved.getId())
                .isEqualTo("Alice");
        assertThat(actual.getColor())
                .as("activeContext=%s seedOrgA=%s employeeId=%s", TenantContext.get(), orgA, saved.getId())
                .isEqualTo("#ff0000");
    }

    @Test
    void findByNameContainingIgnoreCase_matchesAnnaAndJoanne() {
        TenantContext.runAs(orgA, () -> {
            employeeRepository.save(employee("Anna", "#111111"));
            employeeRepository.save(employee("Joanne", "#222222"));
            employeeRepository.save(employee("Mia", "#333333"));
        });

        Page<Employee> page = TenantContext.runAs(
                orgA,
                () -> employeeRepository.findByNameContainingIgnoreCase("ann", PageRequest.of(0, 20))
        );

        assertThat(names(page.getContent()))
                .as("search=ann activeContext=%s seedOrgA=%s returnedNames=%s",
                        TenantContext.get(), orgA, names(page.getContent()))
                .containsExactly("Anna", "Joanne");
    }

    @Test
    void pageable_respectsSizeLimit() {
        TenantContext.runAs(orgA, () -> {
            employeeRepository.save(employee("Alice", "#111111"));
            employeeRepository.save(employee("Bea", "#222222"));
            employeeRepository.save(employee("Cora", "#333333"));
        });

        Page<Employee> page = TenantContext.runAs(orgA, () -> employeeRepository.findAll(PageRequest.of(0, 2)));

        assertThat(page.getContent())
                .as("activeContext=%s seedOrgA=%s pageSize=2 returnedNames=%s",
                        TenantContext.get(), orgA, names(page.getContent()))
                .hasSize(2);
        assertThat(page.getTotalElements())
                .as("activeContext=%s seedOrgA=%s", TenantContext.get(), orgA)
                .isEqualTo(3);
    }

    @Test
    void tenantId_insertAutoPopulates() {
        Employee saved = TenantContext.runAs(orgA, () -> employeeRepository.save(employee("Alice", "#ff0000")));

        UUID storedOrganizationId = jdbcTemplate.queryForObject(
                "select organization_id from employees where id = ?",
                UUID.class,
                saved.getId()
        );

        assertThat(storedOrganizationId)
                .as("operation=insert auto-populate activeContext=%s seedOrgA=%s seedOrgB=%s employeeId=%s storedOrg=%s",
                        TenantContext.get(), orgA, orgB, saved.getId(), storedOrganizationId)
                .isEqualTo(orgA);
    }

    @Test
    void tenantId_selectAutoFilters() {
        UUID orgAEmployeeId = insertEmployee(orgA, "Anna", "#111111");
        UUID orgBEmployeeId = insertEmployee(orgB, "Bea", "#222222");

        List<Employee> employees = TenantContext.runAs(orgA, () -> employeeRepository.findAll());

        assertThat(ids(employees))
                .as("operation=select auto-filter activeContext=%s seedOrgA=%s seedOrgB=%s orgAEmployee=%s orgBEmployee=%s returnedIds=%s",
                        TenantContext.get(), orgA, orgB, orgAEmployeeId, orgBEmployeeId, ids(employees))
                .containsExactly(orgAEmployeeId);
    }

    @Test
    void tenantId_entityManagerFind_respectsDiscriminator() {
        UUID orgBEmployeeId = insertEmployee(orgB, "Bea", "#222222");

        Employee found = TenantContext.runAs(orgA, () -> entityManager.find(Employee.class, orgBEmployeeId));

        assertThat(found)
                .as("operation=EntityManager.find activeContext=%s seedOrgA=%s seedOrgB=%s targetEmployee=%s targetStoredOrg=%s",
                        TenantContext.get(), orgA, orgB, orgBEmployeeId, orgB)
                .isNull();
    }

    @Test
    void tenantId_derivedQuery_respectsDiscriminator() {
        UUID orgAEmployeeId = insertEmployee(orgA, "Anna", "#111111");
        UUID orgBEmployeeId = insertEmployee(orgB, "Joanne", "#222222");

        Page<Employee> page = TenantContext.runAs(
                orgA,
                () -> employeeRepository.findByNameContainingIgnoreCase("ann", PageRequest.of(0, 20))
        );

        assertThat(ids(page.getContent()))
                .as("operation=derived query activeContext=%s seedOrgA=%s seedOrgB=%s orgAEmployee=%s orgBEmployee=%s returnedNames=%s",
                        TenantContext.get(), orgA, orgB, orgAEmployeeId, orgBEmployeeId, names(page.getContent()))
                .containsExactly(orgAEmployeeId);
    }

    @Test
    void tenantId_criteriaQuery_respectsDiscriminator() {
        UUID orgAEmployeeId = insertEmployee(orgA, "Anna", "#111111");
        UUID orgBEmployeeId = insertEmployee(orgB, "Joanne", "#222222");

        List<Employee> employees = TenantContext.runAs(orgA, () -> {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Employee> query = builder.createQuery(Employee.class);
            Root<Employee> root = query.from(Employee.class);
            query.select(root).orderBy(builder.asc(root.get("name")));
            return entityManager.createQuery(query).getResultList();
        });

        assertThat(ids(employees))
                .as("operation=criteria query activeContext=%s seedOrgA=%s seedOrgB=%s orgAEmployee=%s orgBEmployee=%s returnedNames=%s",
                        TenantContext.get(), orgA, orgB, orgAEmployeeId, orgBEmployeeId, names(employees))
                .containsExactly(orgAEmployeeId);
    }

    @Test
    void tenantId_contextUnset_resolverReturnsSentinel_zeroRows() {
        UUID employeeId = insertEmployee(orgA, "Anna", "#111111");

        TenantContext.clear();
        List<Employee> employees = employeeRepository.findAll();

        assertThat(employees)
                .as("operation=sentinel zero rows activeContext=%s seedOrgA=%s seedOrgB=%s employeeId=%s returnedNames=%s",
                        TenantContext.get(), orgA, orgB, employeeId, names(employees))
                .isEmpty();
    }

    private UUID insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, '+15555550101', 'America/New_York') returning id",
                UUID.class,
                name
        );
    }

    private UUID insertEmployee(UUID organizationId, String name, String color) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                color
        );
    }

    private Employee employee(String name, String color) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setColor(color);
        return employee;
    }

    private List<UUID> ids(List<Employee> employees) {
        return employees.stream().map(Employee::getId).toList();
    }

    private List<String> names(List<Employee> employees) {
        return employees.stream().map(Employee::getName).toList();
    }
}
