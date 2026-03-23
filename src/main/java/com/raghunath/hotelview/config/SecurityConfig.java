package com.raghunath.hotelview.config;

import com.raghunath.hotelview.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 1. Public Endpoints (No Token Required)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/admin/login", "/api/v1/employees/login").permitAll()

                        // ✅ CRITICAL FIX: Allow Refreshing without an active Access Token
                        .requestMatchers("/api/v1/admin/refresh-token", "/api/v1/employees/refresh-token").permitAll()

                        // 2. Staff Management (Admin Only)
                        .requestMatchers("/api/v1/employees/register", "/api/v1/employees/list").hasRole("ADMIN")

                        // 3. Kitchen Operations (Chef and Admin)
                        .requestMatchers("/api/v1/orders/kitchen/**").hasAnyRole("CHEF", "ADMIN")

                        // 4. Order & Table Operations (Waiter and Admin)
                        .requestMatchers("/api/v1/orders/draft/**", "/api/v1/orders/confirm/**").hasAnyRole("WAITER", "ADMIN")
                        .requestMatchers("/api/v1/tables/**").hasAnyRole("WAITER", "ADMIN")

                        // 5. Logout (Requires valid Access Token to identify WHO is logging out)
                        .requestMatchers("/api/v1/admin/logout", "/api/v1/employees/logout").authenticated()

                        // 6. Menu Operations
                        .requestMatchers(HttpMethod.POST, "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/**").authenticated()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://hotelview-tau.vercel.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}