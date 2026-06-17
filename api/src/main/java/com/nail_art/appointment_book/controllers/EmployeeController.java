package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.EmployeeReorderRequest;
import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.services.EmployeeService;
import com.nail_art.appointment_book.services.ProductAnalytics;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final ProductAnalytics productAnalytics;

    public EmployeeController(EmployeeService employeeService, ProductAnalytics productAnalytics) {
        this.employeeService = employeeService;
        this.productAnalytics = productAnalytics;
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
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<?> createEmployee(@Valid @RequestBody Employee employee, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        Employee created = employeeService.createEmployee(employee);
        productAnalytics.capture("employee_created", Map.of(
                "employee_id", created.getId().toString(),
                "employee_name", String.valueOf(created.getName())));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/edit/{id}")
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<Employee> editEmployee(@PathVariable UUID id, @Valid @RequestBody Employee employee) {
        Optional<Employee> editedEmployee = employeeService.editEmployee(id, employee);
        if (editedEmployee.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        productAnalytics.capture("employee_edited", Map.of(
                "employee_id", editedEmployee.get().getId().toString(),
                "employee_name", String.valueOf(editedEmployee.get().getName())));
        return ResponseEntity.ok(editedEmployee.get());
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        // Resolve the name before the row is gone so the event stays readable.
        String employeeName = employeeService.getEmployeeById(id).map(Employee::getName).orElse(null);
        if (!employeeService.deleteEmployee(id)) {
            return ResponseEntity.notFound().build();
        }
        productAnalytics.capture("employee_deleted", Map.of(
                "employee_id", id.toString(),
                "employee_name", String.valueOf(employeeName)));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<List<Employee>> getEmployeeByName(@PathVariable String name) {
        return ResponseEntity.ok(employeeService.getEmployeeByName(name));
    }

    @PostMapping("/reorder")
    @PreAuthorize("hasAuthority('owner')")
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
