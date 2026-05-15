package dev.autotix.infrastructure.auth;

import dev.autotix.domain.AutotixException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * Per-request JWT validation, populating SecurityContext on success.
 * Skips: /api/auth/**, /v2/webhook/**, /error, static assets.
 * Reads token from:
 *   - Authorization: Bearer <token>
 *   - ?token=<token> query param (for SSE EventSource connections)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only skip the public auth endpoints; /api/auth/me and /api/auth/password require auth
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.startsWith("/v2/webhook/")
                || path.startsWith("/ws/livechat/")
                || path.startsWith("/demo/")
                || path.startsWith("/widget/")
                || path.equals("/error")
                || path.startsWith("/favicon")
                || path.startsWith("/static/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(req);

        if (token == null) {
            // No token — let Spring Security handle 401 via access-denied
            chain.doFilter(req, res);
            return;
        }

        JwtTokenProvider.Claims claims;
        try {
            claims = tokenProvider.verify(token);
        } catch (AutotixException.AuthException ex) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"" + ex.getMessage() + "\"}");
            return; // do NOT call chain.doFilter
        }

        SimpleGrantedAuthority authority = toAuthority(claims.role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                claims.userId,
                null,
                Collections.singletonList(authority)
        );
        // Store full claims as details so CurrentUser can read role
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String extractToken(HttpServletRequest request) {
        // 1. Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        // 2. ?token= query param (SSE/EventSource)
        String param = request.getParameter("token");
        if (param != null && !param.isEmpty()) {
            return param;
        }
        return null;
    }

    private SimpleGrantedAuthority toAuthority(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }
}
