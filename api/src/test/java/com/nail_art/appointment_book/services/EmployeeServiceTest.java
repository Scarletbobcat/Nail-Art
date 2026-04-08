package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CounterService counterService;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee makeEmployee(String name, String color) {
        Employee emp = new Employee();
        emp.setName(name);
        emp.setColor(color);
        return emp;
    }

    @Nested
    class CreateEmployee {

        @Test
        void createsEmployeeWithGeneratedId() {
            Employee emp = makeEmployee("Alice", "#FF0000");
            when(counterService.getNextSequence("Employees")).thenReturn(5L);
            when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Employee result = employeeService.createEmployee(emp);

            assertEquals(5L, result.getId());
            assertEquals("Alice", result.getName());
            assertEquals("#FF0000", result.getColor());
            verify(employeeRepository).save(emp);
        }
    }

    @Nested
    class EditEmployee {

        @Test
        void updatesNameAndColor() {
            Employee existing = makeEmployee("Alice", "#FF0000");
            existing.setId(5);

            Employee updated = makeEmployee("Alice Smith", "#00FF00");
            updated.setId(5);

            when(employeeRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Employee result = employeeService.editEmployee(updated);

            assertNotNull(result);
            assertEquals("Alice Smith", result.getName());
            assertEquals("#00FF00", result.getColor());
        }

        @Test
        void returnsNullWhenNotFound() {
            Employee emp = makeEmployee("Nobody", "#000000");
            emp.setId(999);
            when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

            assertNull(employeeService.editEmployee(emp));
            verify(employeeRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteEmployee {

        @Test
        void deletesAndReturnsEmployee() {
            Employee emp = makeEmployee("Alice", "#FF0000");
            emp.setId(5);

            when(employeeRepository.findById(5L)).thenReturn(Optional.of(emp));

            Employee result = employeeService.deleteEmployee(emp);

            assertNotNull(result);
            assertEquals("Alice", result.getName());
            verify(employeeRepository).delete(emp);
        }

        @Test
        void returnsNullWhenNotFound() {
            Employee emp = makeEmployee("Nobody", "#000000");
            emp.setId(999);
            when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

            assertNull(employeeService.deleteEmployee(emp));
            verify(employeeRepository, never()).delete(any());
        }
    }
}
