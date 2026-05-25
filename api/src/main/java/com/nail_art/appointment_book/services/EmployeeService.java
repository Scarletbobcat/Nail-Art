package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.EmployeeReorderRequest;
import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Page<Employee> getAllEmployees(Pageable pageable) {
        return employeeRepository.findAll(pageable);
    }

    public Page<Employee> searchEmployees(String name, Pageable pageable) {
        return employeeRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public Employee createEmployee(Employee employee) {
        if (employee.getDisplayOrder() == null) {
            employee.setDisplayOrder(employeeRepository.findMaxDisplayOrder() + 1);
        }
        return employeeRepository.save(employee);
    }

    public Optional<Employee> editEmployee(UUID id, Employee employee) {
        return employeeRepository.findScopedById(id).map(existing -> {
            existing.setColor(employee.getColor());
            existing.setName(employee.getName());
            if (employee.getDisplayOrder() != null) {
                existing.setDisplayOrder(employee.getDisplayOrder());
            }
            return employeeRepository.save(existing);
        });
    }

    public boolean deleteEmployee(UUID id) {
        return employeeRepository.findScopedById(id)
                .map(employee -> {
                    employeeRepository.delete(employee);
                    return true;
                })
                .orElse(false);
    }

    public List<Employee> getEmployeeByName(String name) {
        return employeeRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Atomically rewrite display_order for the given employees. Relies on the
     * deferrable unique constraint (V5) so intermediate collisions inside the
     * transaction are tolerated; the constraint is checked at commit.
     *
     * @return the reordered employees, sorted by their new displayOrder.
     * @throws IllegalArgumentException if any id is not in the caller's tenant
     *         or if the same id appears twice in the payload.
     */
    @Transactional
    public List<Employee> reorder(EmployeeReorderRequest request) {
        List<EmployeeReorderRequest.Item> items = request.items();

        Set<UUID> seen = new HashSet<>();
        for (EmployeeReorderRequest.Item item : items) {
            if (!seen.add(item.id())) {
                throw new IllegalArgumentException("Duplicate id in reorder payload: " + item.id());
            }
        }

        List<Employee> found = employeeRepository.findAllById(seen);
        if (found.size() != seen.size()) {
            throw new IllegalArgumentException("One or more employees not found in tenant");
        }

        Map<UUID, Employee> byId = found.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        for (EmployeeReorderRequest.Item item : items) {
            byId.get(item.id()).setDisplayOrder(item.displayOrder());
        }

        return employeeRepository.saveAll(byId.values()).stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .toList();
    }
}
