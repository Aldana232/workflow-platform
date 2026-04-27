package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.dto.LoginRequest;
import com.workflow.workflowplatform.dto.LoginResponse;
import com.workflow.workflowplatform.dto.RegisterRequest;
import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.repository.UserRepository;
import com.workflow.workflowplatform.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    public LoginResponse login(LoginRequest request) {
        logger.info("Login attempt para email: {}", request.getEmail());
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        java.util.Collections.singletonList(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + user.getRole().name()
                                )
                        )
                );
        String token = jwtUtil.generateToken(principal,
                user.getId(), user.getCompanyId(), user.getDepartmentId());

        logger.info("Token generado para {}: {}", user.getEmail(), token.substring(0, Math.min(50, token.length())) + "...");

        LoginResponse response = new LoginResponse(
                token,
                user.getRole(),
                user.getFirstName() + " " + user.getLastName(),
                user.getId()
        );
        
        logger.info("LoginResponse creada. Token presente: {}", response.getToken() != null && !response.getToken().isEmpty());
        
        return response;
    }

    public User register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getName())
                .lastName("")
                .role(request.getRole())
                .companyId(request.getCompanyId())
                .departmentId(request.getDepartmentId())
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }
}
