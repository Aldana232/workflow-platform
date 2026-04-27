package com.workflow.workflowplatform.config;

import com.workflow.workflowplatform.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return builder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/companies", "/api/companies/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers("/api/departments", "/api/departments/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/processes", "/api/processes/**").hasAnyRole("SUPERADMIN", "ADMIN", "FUNCIONARIO")
                .requestMatchers("/api/processes", "/api/processes/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers("/api/users", "/api/users/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers("/api/form-schemas", "/api/form-schemas/**").hasAnyRole("SUPERADMIN", "ADMIN", "FUNCIONARIO")
                .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/files/**").hasAnyRole("SUPERADMIN", "ADMIN", "FUNCIONARIO")
                .requestMatchers("/api/tramites/code/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/tramites/**").hasAnyRole("SUPERADMIN", "ADMIN")
                .requestMatchers("/api/tramites", "/api/tramites/**").hasAnyRole("SUPERADMIN", "ADMIN", "FUNCIONARIO")
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/collab/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
