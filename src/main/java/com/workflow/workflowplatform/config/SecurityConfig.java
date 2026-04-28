package com.workflow.workflowplatform.config;

import com.workflow.workflowplatform.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtFilter jwtFilter, UserDetailsService userDetailsService) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder =
            http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService)
               .passwordEncoder(passwordEncoder());
        return builder.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200",
            "https://main.d3rs2veleasrg5.amplifyapp.com",
            "https://workflow-demo.site",
            "https://www.workflow-demo.site",
            "https://api.workflow-demo.site"
        ));

        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        config.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));

        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Access-Control-Allow-Origin"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
