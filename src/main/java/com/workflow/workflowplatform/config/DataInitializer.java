package com.workflow.workflowplatform.config;

import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.model.enums.UserRole;
import com.workflow.workflowplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        logger.info("=== INICIANDO DATA INITIALIZER ===");
        
        try {
            // SUPERADMIN — crear si no existe; reparar contraseña si no es BCrypt
            Optional<User> existingSuperAdmin = userRepository.findByEmail("superadmin@workflow.com");
            if (existingSuperAdmin.isPresent()) {
                User sa = existingSuperAdmin.get();
                if (!isBcrypt(sa.getPassword())) {
                    sa.setPassword(passwordEncoder.encode("super123"));
                    sa.setActive(true);
                    sa.setUpdatedAt(LocalDateTime.now());
                    userRepository.save(sa);
                    logger.info("✓ SuperAdmin password re-encodeada con BCrypt");
                } else {
                    logger.info("✓ SuperAdmin OK (password BCrypt)");
                }
            } else {
                User superAdmin = User.builder()
                        .email("superadmin@workflow.com")
                        .password(passwordEncoder.encode("super123"))
                        .firstName("Super")
                        .lastName("Administrador TI")
                        .role(UserRole.SUPERADMIN)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(superAdmin);
                logger.info("✓ SuperAdmin creado con BCrypt");
            }

            // ADMIN — crear si no existe; reparar contraseña y companyId si faltan
            Optional<User> existingAdmin = userRepository.findByEmail("admin@workflow.com");
            if (existingAdmin.isPresent()) {
                User adm = existingAdmin.get();
                boolean changed = false;
                if (!isBcrypt(adm.getPassword())) {
                    adm.setPassword(passwordEncoder.encode("admin123"));
                    adm.setActive(true);
                    changed = true;
                    logger.info("✓ Admin password re-encodeada con BCrypt.");
                }
                // Reparar companyId si falta — sin él el WebSocket de empresa no funciona
                if (adm.getCompanyId() == null || adm.getCompanyId().isEmpty()) {
                    adm.setCompanyId("saguapac");
                    changed = true;
                    logger.info("✓ Admin companyId reparado → 'saguapac'");
                }
                if (changed) {
                    adm.setUpdatedAt(LocalDateTime.now());
                    userRepository.save(adm);
                } else {
                    logger.info("✓ Admin OK (password BCrypt, companyId presente)");
                }
            } else {
                User admin = User.builder()
                        .email("admin@workflow.com")
                        .password(passwordEncoder.encode("admin123"))
                        .firstName("Administrador")
                        .lastName("Saguapac")
                        .role(UserRole.ADMIN)
                        .companyId("saguapac")   // requerido para canal WebSocket /topic/company/saguapac
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
                logger.info("✓ Admin creado con BCrypt. Usa: admin@workflow.com / admin123");
            }

            // Crear usuario FUNCIONARIO para pruebas
            Optional<User> existingFuncionario = userRepository.findByEmail("carlos@workflow.com");
            
            if (existingFuncionario.isPresent()) {
                logger.info("✓ Funcionario ya existe en MongoDB. Email: carlos@workflow.com");
            } else {
                logger.info("✗ Funcionario no encontrado. Creando nuevo funcionario...");
                
                User funcionario = User.builder()
                        .email("carlos@workflow.com")
                        .password(passwordEncoder.encode("carlos123"))
                        .firstName("Carlos")
                        .lastName("Atención al Cliente")
                        .role(UserRole.FUNCIONARIO)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                User savedFuncionario = userRepository.save(funcionario);
                logger.info("✓ Funcionario creado exitosamente!");
                logger.info("  - ID MongoDB: {}", savedFuncionario.getId());
                logger.info("  - Email: {}", savedFuncionario.getEmail());
                logger.info("  - Role: {}", savedFuncionario.getRole());
                logger.info("  - Contraseña encriptada con BCrypt");
            }
            
            // Verificación final
            long totalUsers = userRepository.count();
            logger.info("=== TOTAL DE USUARIOS EN MONGODB: {} ===", totalUsers);
            
        } catch (Exception e) {
            logger.error("❌ Error en DataInitializer: {}", e.getMessage(), e);
            throw e;
        }
    }

    /** Verifica que el hash sea BCrypt (empieza con $2a$, $2b$ o $2y$) */
    private boolean isBcrypt(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }
}

