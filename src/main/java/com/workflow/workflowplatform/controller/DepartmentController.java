package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.model.Department;
import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.repository.DepartmentRepository;
import com.workflow.workflowplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Department>> getAll(Authentication authentication) {
        User admin = userRepository.findByEmail(authentication.getName()).orElse(null);
        List<Department> depts = (admin != null && admin.getCompanyId() != null)
                ? departmentRepository.findByCompanyId(admin.getCompanyId())
                : departmentRepository.findAll();
        return ResponseEntity.ok(depts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Department> getById(@PathVariable String id) {
        return departmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Department> create(@RequestBody Department department,
                                             Authentication authentication) {
        User admin = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (admin != null && department.getCompanyId() == null) {
            department.setCompanyId(admin.getCompanyId());
        }
        department.setActive(true);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentRepository.save(department));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Department> update(@PathVariable String id,
                                             @RequestBody Department update) {
        return departmentRepository.findById(id).map(dept -> {
            if (update.getName() != null)        dept.setName(update.getName());
            if (update.getDescription() != null) dept.setDescription(update.getDescription());
            if (update.getManager() != null)     dept.setManager(update.getManager());
            if (update.getActive() != null)      dept.setActive(update.getActive());
            dept.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(departmentRepository.save(dept));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        return departmentRepository.findById(id).map(dept -> {
            dept.setActive(false);
            dept.setUpdatedAt(LocalDateTime.now());
            departmentRepository.save(dept);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
