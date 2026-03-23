package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.entity.Employee;
import com.raghunath.hotelview.service.admin.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    // ADMIN ONLY: Add new staff
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> addEmployee(@RequestBody Employee employee) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(employeeService.registerEmployee(employee, hotelId));
    }

    // PUBLIC: Staff Login (Waiter/Chef)
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        return ResponseEntity.ok(employeeService.login(
                credentials.get("username"),
                credentials.get("password")
        ));
    }

    // ADMIN ONLY: View all staff members
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Employee> listStaff() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeService.getMyStaff(hotelId);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(employeeService.refreshEmployeeToken(request.get("refreshToken")));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody Map<String, String> request) {
        String empId = SecurityContextHolder.getContext().getAuthentication().getName();
        String token = request.get("refreshToken");

        // This call must match the (String, String) signature in the Service
        employeeService.logoutEmployee(empId, token);

        return ResponseEntity.ok("Staff logged out successfully");
    }
}