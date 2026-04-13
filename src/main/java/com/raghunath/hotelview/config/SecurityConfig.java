package com.raghunath.hotelview.config;

import com.raghunath.hotelview.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    // ✅ FIX: Tells Spring "I handle auth via JWT, stop creating default password"
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("JWT authentication is used");
        };
    }

    // ✅ FIX: Exposes AuthenticationManager in case any service needs it
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
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
                        .requestMatchers("/api/v1/admin/login", "/api/v1/employees/login", "/api/v1/sync/test-bump").permitAll()

                        // Allow Refreshing without an active Access Token
                        .requestMatchers("/api/v1/admin/refresh-token", "/api/v1/employees/refresh-token").permitAll()

                        // 2. Staff Management (Admin Only)
                        .requestMatchers("/api/v1/employees/register","/api/v1/tables/**", "/api/v1/employees/**", "/api/v1/employees/list").hasRole("ADMIN")

                        // 3. Kitchen Operations: Waiters can VIEW, but only CHEF/ADMIN can UPDATE
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/kitchen/**").hasAnyRole("WAITER", "CHEF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/kitchen/**").hasAnyRole("CHEF", "ADMIN")

                        // 4. Order Operations: Waiters and Admins
                        .requestMatchers("/api/v1/orders/draft/**", "api/v1/menu/version", "/api/v1/tables", "/api/v1/orders/completed/**", "/api/v1/orders/completed/delivery/today", "/api/v1/orders/dashboard/stats", "/api/v1/orders/confirm/**", "/api/v1/orders/table/**", "/api/v1/orders/checkout").hasAnyRole("WAITER", "ADMIN")

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
                "https://hotelview-tau.vercel.app",
                "http://localhost",
                "https://localhost",
                "capacitor://localhost"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}