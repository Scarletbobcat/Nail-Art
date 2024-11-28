package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Employee;
import com.nail_art.appointment_book.services.EmployeeService;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/employees")
@RestController
public class EmployeeController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);
    @Autowired
    EmployeeService employeeService;

    @GetMapping("/")
    public ResponseEntity<List<Employee>> getEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
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
