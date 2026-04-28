package com.workflow.workflowplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // allowedOriginPatterns es la forma correcta en Spring Boot 3
        // cuando allowCredentials = true. Nunca usar setAllowedOrigins("*")
        // junto con allowCredentials, ya que el estándar CORS lo prohíbe.
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:4200",
            "https://main.d3rs2veleasrg5.amplifyapp.com",
            "https://workflow-demo.site"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin"
        ));

        // Exponer Authorization para que Angular pueda leer el token en respuestas
        config.setExposedHeaders(List.of("Authorization"));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
