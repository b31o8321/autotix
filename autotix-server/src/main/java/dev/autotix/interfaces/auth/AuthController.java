package dev.autotix.interfaces.auth;

import dev.autotix.application.user.ChangePasswordUseCase;
import dev.autotix.application.user.LoginUseCase;
import dev.autotix.infrastructure.auth.CurrentUser;
import dev.autotix.infrastructure.auth.JwtTokenProvider;
import dev.autotix.interfaces.auth.dto.ChangePasswordRequest;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.auth.dto.RefreshRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST endpoints.
 *  POST /api/auth/login     — exchange email + password for token pair
 *  POST /api/auth/refresh   — refresh access token
 *  GET  /api/auth/me        — current user info
 *  POST /api/auth/password  — change own password (requires auth)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final CurrentUser currentUser;

    public AuthController(LoginUseCase loginUseCase,
                          ChangePasswordUseCase changePasswordUseCase,
                          CurrentUser currentUser) {
        this.loginUseCase = loginUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        LoginUseCase.TokenPair pair = loginUseCase.login(req.email, req.password);
        return toLoginResponse(pair);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest req) {
        LoginUseCase.TokenPair pair = loginUseCase.refresh(req.refreshToken);
        return toLoginResponse(pair);
    }

    @GetMapping("/me")
    public LoginResponse.UserInfo me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth.getDetails();

        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.id = currentUser.id().value();
        info.role = currentUser.role().name();

        if (details instanceof JwtTokenProvider.Claims) {
            JwtTokenProvider.Claims claims = (JwtTokenProvider.Claims) details;
            info.email = claims.email;
        }
        // displayName not in JWT claims — would need DB lookup; omit in v1
        return info;
    }

    @PostMapping("/password")
    public void changePassword(@RequestBody ChangePasswordRequest req) {
        changePasswordUseCase.changePassword(currentUser.id(), req.currentPassword, req.newPassword);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LoginResponse toLoginResponse(LoginUseCase.TokenPair pair) {
        LoginResponse resp = new LoginResponse();
        resp.accessToken = pair.accessToken;
        resp.refreshToken = pair.refreshToken;
        resp.accessExpiresAt = pair.accessExpiresAt;
        resp.refreshExpiresAt = pair.refreshExpiresAt;
        return resp;
    }
}
