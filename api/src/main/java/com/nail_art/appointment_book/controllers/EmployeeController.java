package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.services.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/employees")
@RestController
public class EmployeeController {
    @Autowired
    EmployeeService employeeService;

    @GetMapping("/")
    public ResponseEntity<Page<Employee>> getEmployees(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("id").ascending());
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(employeeService.searchEmployees(name, pageable));
        }
        return ResponseEntity.ok(employeeService.getAllEmployees(pageable));
    }

    @PostMapping("/create")
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        return ResponseEntity.ok(employeeService.createEmployee(employee));
    }

    @PutMapping("/edit")
    public ResponseEntity<Employee> editEmployee(@RequestBody Employee employee) {
        return ResponseEntity.ok(employeeService.editEmployee(employee));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Employee> deleteEmployee(@RequestBody Employee employee) {
        return ResponseEntity.ok(employeeService.deleteEmployee(employee));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<Employee[]> getEmployeeByName(@PathVariable String name) {
        return ResponseEntity.ok(employeeService.getEmployeeByName(name));
    }
}
