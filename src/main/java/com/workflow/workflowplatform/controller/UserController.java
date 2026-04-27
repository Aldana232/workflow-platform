package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.UserResponseDTO;
import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.model.enums.UserRole;
import com.workflow.workflowplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private UserResponseDTO toDTO(User u) {
        return UserResponseDTO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .departmentId(u.getDepartmentId())
                .companyId(u.getCompanyId())
                .active(u.getActive())
                .createdAt(u.getCreatedAt())
                .build();
    }

    private User adminUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    /** Lista todos los usuarios de la misma empresa del admin */
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAll(Authentication authentication) {
        User admin = adminUser(authentication);
        if (admin == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<User> users = (admin.getCompanyId() != null)
                ? userRepository.findByCompanyId(admin.getCompanyId())
                : userRepository.findAll();
        return ResponseEntity.ok(users.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    /** Lista usuarios de un departamento específico */
    @GetMapping("/department/{deptId}")
    public ResponseEntity<List<UserResponseDTO>> getByDepartment(@PathVariable String deptId) {
        return ResponseEntity.ok(
            userRepository.findByDepartmentId(deptId).stream()
                .map(this::toDTO).collect(Collectors.toList())
        );
    }

    /** Crea un nuevo usuario (funcionario) */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    Authentication authentication) {
        User admin = adminUser(authentication);
        String email = (String) body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email requerido");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body("Email ya registrado");
        }

        String rawPassword = (String) body.getOrDefault("password", "Saguapac2024!");
        String roleStr     = (String) body.getOrDefault("role", "FUNCIONARIO");
        UserRole role;
        try { role = UserRole.valueOf(roleStr); } catch (Exception e) { role = UserRole.FUNCIONARIO; }

        String companyId = body.containsKey("companyId")
                ? (String) body.get("companyId")
                : (admin != null ? admin.getCompanyId() : null);

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .firstName((String) body.getOrDefault("firstName", ""))
                .lastName((String) body.getOrDefault("lastName", ""))
                .role(role)
                .companyId(companyId)
                .departmentId((String) body.get("departmentId"))
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(userRepository.save(user)));
    }

    /** Actualiza datos del usuario (nombre, rol, departamento, activo) */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @RequestBody Map<String, Object> body) {
        return userRepository.findById(id).map(user -> {
            if (body.containsKey("firstName"))    user.setFirstName((String) body.get("firstName"));
            if (body.containsKey("lastName"))     user.setLastName((String) body.get("lastName"));
            if (body.containsKey("departmentId")) user.setDepartmentId((String) body.get("departmentId"));
            if (body.containsKey("active"))       user.setActive((Boolean) body.get("active"));
            if (body.containsKey("role")) {
                try { user.setRole(UserRole.valueOf((String) body.get("role"))); }
                catch (Exception ignored) {}
            }
            user.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok((Object) toDTO(userRepository.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Asigna o quita departamento de un usuario */
    @PatchMapping("/{id}/department")
    public ResponseEntity<?> assignDepartment(@PathVariable String id,
                                              @RequestBody Map<String, Object> body) {
        return userRepository.findById(id).map(user -> {
            user.setDepartmentId((String) body.get("departmentId"));
            user.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok((Object) toDTO(userRepository.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Desactiva un usuario (soft delete) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        return userRepository.findById(id).map(user -> {
            user.setActive(false);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
