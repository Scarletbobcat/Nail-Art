package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.EmployeeReorderRequest;
import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
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

        when(employeeRepository.findMaxDisplayOrder()).thenReturn(-1);
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
        assertThat(result.getDisplayOrder())
                .as("new employee without explicit display order should append after max existing order")
                .isZero();
        verify(employeeRepository).save(employee);
    }

    @Test
    void createEmployee_explicitDisplayOrder_preservesOrder() {
        UUID organizationId = UUID.randomUUID();
        Employee employee = employee("Alice", "#ff0000");
        employee.setDisplayOrder(7);

        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Employee result = TenantContext.runAs(organizationId, () -> employeeService.createEmployee(employee));

        assertThat(result.getDisplayOrder())
                .as("explicit display order should be honored for calendar column ordering")
                .isEqualTo(7);
    }

    @Test
    void editEmployee_updatesDisplayOrderWhenProvided() {
        UUID employeeId = UUID.randomUUID();
        Employee existing = employee("Alice", "#ff0000");
        existing.setDisplayOrder(3);
        Employee patch = employee("Alice", "#00ff00");
        patch.setDisplayOrder(1);

        when(employeeRepository.findScopedById(employeeId)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Employee> result = employeeService.editEmployee(employeeId, patch);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getDisplayOrder())
                .as("edit should persist calendar display order changes")
                .isEqualTo(1);
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

    @Test
    void createEmployee_afterDelete_doesNotBackfillGap() {
        UUID organizationId = UUID.randomUUID();
        Employee newEmployee = employee("Dan", "#abcdef");

        // Existing orders: 0, 2 (gap at 1 from a prior delete). max+1 = 3.
        when(employeeRepository.findMaxDisplayOrder()).thenReturn(2);
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Employee result = TenantContext.runAs(organizationId, () -> employeeService.createEmployee(newEmployee));

        assertThat(result.getDisplayOrder())
                .as("after delete-induced gap, new create appends to end (max+1), not into the gap")
                .isEqualTo(3);
    }

    @Test
    void reorder_appliesEveryItemDisplayOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Employee e1 = employee("Anna", "#111");
        e1.setId(id1);
        e1.setDisplayOrder(0);
        Employee e2 = employee("Bea", "#222");
        e2.setId(id2);
        e2.setDisplayOrder(1);

        when(employeeRepository.findAllById(anyIterable())).thenReturn(List.of(e1, e2));
        when(employeeRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            Iterable<Employee> iter = invocation.getArgument(0);
            return java.util.stream.StreamSupport.stream(iter.spliterator(), false).toList();
        });

        EmployeeReorderRequest request = new EmployeeReorderRequest(List.of(
                new EmployeeReorderRequest.Item(id1, 1),
                new EmployeeReorderRequest.Item(id2, 0)
        ));

        List<Employee> result = employeeService.reorder(request);

        assertThat(result)
                .as("reorder result should be sorted ascending by new displayOrder")
                .extracting(Employee::getId)
                .containsExactly(id2, id1);
        assertThat(e1.getDisplayOrder()).isEqualTo(1);
        assertThat(e2.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void reorder_duplicateIdInPayload_throwsBeforeAnySave() {
        UUID id = UUID.randomUUID();
        EmployeeReorderRequest request = new EmployeeReorderRequest(List.of(
                new EmployeeReorderRequest.Item(id, 0),
                new EmployeeReorderRequest.Item(id, 1)
        ));

        assertThatThrownBy(() -> employeeService.reorder(request))
                .as("payload with duplicate ids must be rejected before any persistence call")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate id");
        verify(employeeRepository, never()).saveAll(any());
    }

    @Test
    void reorder_idNotFound_throwsAndSkipsSave() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        Employee e1 = employee("Anna", "#111");
        e1.setId(known);
        e1.setDisplayOrder(0);

        // findAllById is tenant-scoped via @TenantId; cross-tenant or deleted ids are silently dropped.
        when(employeeRepository.findAllById(anyIterable())).thenReturn(List.of(e1));

        EmployeeReorderRequest request = new EmployeeReorderRequest(List.of(
                new EmployeeReorderRequest.Item(known, 1),
                new EmployeeReorderRequest.Item(unknown, 0)
        ));

        assertThatThrownBy(() -> employeeService.reorder(request))
                .as("payload referencing ids outside the tenant or already-deleted must be rejected")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(employeeRepository, never()).saveAll(any());
    }

    private Employee employee(String name, String color) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setColor(color);
        return employee;
    }
}
