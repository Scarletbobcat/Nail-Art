package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        return employeeRepository.save(employee);
    }

    public Optional<Employee> editEmployee(UUID id, Employee employee) {
        return employeeRepository.findScopedById(id).map(existing -> {
            existing.setColor(employee.getColor());
            existing.setName(employee.getName());
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
}
