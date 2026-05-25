package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.EmployeeReorderRequest;
import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.services.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequestMapping("/employees")
@RestController
public class EmployeeController {
    private static final Sort EMPLOYEE_DISPLAY_SORT = Sort.by(
            Sort.Order.asc("displayOrder"),
            Sort.Order.asc("name"),
            Sort.Order.asc("id")
    );

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/")
    public ResponseEntity<Page<Employee>> getEmployees(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), EMPLOYEE_DISPLAY_SORT);
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(employeeService.searchEmployees(name, pageable));
        }
        return ResponseEntity.ok(employeeService.getAllEmployees(pageable));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEmployee(@Valid @RequestBody Employee employee, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        return new ResponseEntity<>(employeeService.createEmployee(employee), HttpStatus.CREATED);
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<Employee> editEmployee(@PathVariable UUID id, @Valid @RequestBody Employee employee) {
        Optional<Employee> editedEmployee = employeeService.editEmployee(id, employee);
        if (editedEmployee.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(editedEmployee.get());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        if (!employeeService.deleteEmployee(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<List<Employee>> getEmployeeByName(@PathVariable String name) {
        return ResponseEntity.ok(employeeService.getEmployeeByName(name));
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@Valid @RequestBody EmployeeReorderRequest request, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        try {
            return ResponseEntity.ok(employeeService.reorder(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
