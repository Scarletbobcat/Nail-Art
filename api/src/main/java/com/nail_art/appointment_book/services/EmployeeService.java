package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private CounterService counterService;

    public List<Employee> getAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();

        return employees;
    }

    public Employee createEmployee(Employee employee) {
        employee.setId(counterService.getNextSequence("Employees"));
        return employeeRepository.save(employee);
    }

    public Employee editEmployee(Employee employee) {
        Employee tempEmp = employeeRepository.findById(employee.getId()).orElse(null);
        if (tempEmp == null) {
            return null;
        }
        tempEmp.setColor(employee.getColor());
        tempEmp.setName(employee.getName());
        return employeeRepository.save(tempEmp);
    }

    public Employee deleteEmployee(Employee employee) {
        Employee tempEmp = employeeRepository.findById(employee.getId()).orElse(null);
        if (tempEmp == null) {
            return null;
        }
        employeeRepository.delete(tempEmp);
        return tempEmp;
    }

    public Employee[] getEmployeeByName(String name) {
        return employeeRepository.findAllByName(name);
    }
}
