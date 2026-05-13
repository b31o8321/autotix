package dev.autotix.infrastructure.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 5 (Spring Boot 2.4) stateless JWT configuration.
 *
 * Authorization map:
 *   /v2/webhook/**   → permitAll  (signature verified inside controller)
 *   /api/auth/**     → permitAll
 *   /api/admin/**    → ROLE_ADMIN
 *   /api/desk/**     → ROLE_ADMIN or ROLE_AGENT
 *   /api/inbox/**    → ROLE_ADMIN or ROLE_AGENT
 *   /api/reports/**  → ROLE_ADMIN or ROLE_AGENT or ROLE_VIEWER
 *   everything else  → authenticated
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .and()
            .authorizeRequests()
                .antMatchers("/v2/webhook/**", "/api/auth/**", "/error").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/desk/**", "/api/inbox/**").hasAnyRole("ADMIN", "AGENT")
                .antMatchers("/api/reports/**").hasAnyRole("ADMIN", "AGENT", "VIEWER")
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
