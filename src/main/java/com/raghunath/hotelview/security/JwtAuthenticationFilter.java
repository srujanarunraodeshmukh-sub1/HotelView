package com.raghunath.hotelview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager; // 👈 Core memory cache manager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CacheManager cacheManager; // 👈 Inject your native Spring cache manager

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (jwtUtil.validateToken(token)) {
                String hotelId = jwtUtil.extractHotelId(token);

                // ⚡ Read from RAM Cache instead of hitting MongoDB disk space!
                Cache activeSessionsCache = cacheManager.getCache("activeSessionsCache");
                Boolean isSessionActive = null;

                if (activeSessionsCache != null) {
                    isSessionActive = activeSessionsCache.get(hotelId, Boolean.class);
                }

                // Fallback: If cache is empty, assume valid or let refresh endpoint handle it
                if (isSessionActive == null || isSessionActive) {
                    String role = jwtUtil.extractRole(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    hotelId,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"SESSION_EXPIRED\", \"message\": \"Your session was terminated by another device.\"}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}