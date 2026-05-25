package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createEmployee_persistsWithOrgFromPrincipal() {
        UUID organizationId = UUID.randomUUID();
        Employee employee = employee("Alice", "#ff0000");

        when(employeeRepository.save(any())).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setOrganizationId(TenantContext.get());
            return saved;
        });

        Employee result = TenantContext.runAs(organizationId, () -> employeeService.createEmployee(employee));

        assertThat(result.getOrganizationId())
                .as("activeContext=%s expectedOrg=%s employeeId=%s",
                        TenantContext.get(), organizationId, result.getId())
                .isEqualTo(organizationId);
        assertThat(result.getName()).isEqualTo("Alice");
        verify(employeeRepository).save(employee);
    }

    @Test
    void editEmployee_idFromAnotherOrg_returnsEmpty() {
        UUID attackerOrg = UUID.randomUUID();
        UUID targetEmployeeId = UUID.randomUUID();
        Employee patch = employee("Mallory", "#000000");

        when(employeeRepository.findScopedById(targetEmployeeId)).thenReturn(Optional.empty());

        Optional<Employee> result = TenantContext.runAs(
                attackerOrg,
                () -> employeeService.editEmployee(targetEmployeeId, patch)
        );

        assertThat(result)
                .as("operation=service cross-org edit activeContext=%s attackerOrg=%s targetEmployee=%s",
                        TenantContext.get(), attackerOrg, targetEmployeeId)
                .isEmpty();
        verify(employeeRepository, never()).save(any());
    }

    private Employee employee(String name, String color) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setColor(color);
        return employee;
    }
}
