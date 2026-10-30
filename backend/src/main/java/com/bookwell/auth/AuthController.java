package com.bookwell.auth;

import com.bookwell.common.api.ApiError;
import com.bookwell.common.exception.ApiException;
import com.bookwell.common.security.AuthUser;
import com.bookwell.common.security.RequestMetadata;
import com.bookwell.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                          HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.registerCustomer(request, requestInfo(httpRequest));
        return withSession(result);
    }

    @PostMapping("/register/provider")
    public ResponseEntity<AuthDtos.AuthResponse> registerProvider(
            @Valid @RequestBody AuthDtos.ProviderRegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.registerProvider(request, requestInfo(httpRequest));
        return withSession(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                       HttpServletRequest httpRequest) {
        AuthService.AuthResult result = authService.login(request, requestInfo(httpRequest));
        return withSession(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            AuthService.AuthResult result = authService.refresh(readRefreshCookie(request), requestInfo(request));
            return withSession(result);
        } catch (ApiException ex) {
            ResponseCookie cleared = refreshCookie("", Duration.ZERO);
            ApiError error = ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI());
            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cleared.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        ResponseCookie cleared = refreshCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<AuthDtos.CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/me")
    public AuthDtos.UserSummary me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    private ResponseEntity<AuthDtos.AuthResponse> withSession(AuthService.AuthResult result) {
        ResponseCookie cookie = refreshCookie(result.refreshToken(), properties.security().refreshTokenTtl());
        long expiresIn = result.accessToken().expiresInSeconds();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.AuthResponse(
                        result.accessToken().value(), "Bearer", expiresIn, result.user()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        var builder = ResponseCookie.from(properties.security().refreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite(properties.security().cookieSameSite())
                .path(REFRESH_PATH)
                .maxAge(maxAge);
        String domain = properties.security().cookieDomain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.security().refreshCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private AuthService.RequestInfo requestInfo(HttpServletRequest request) {
        return new AuthService.RequestInfo(
                RequestMetadata.userAgent(request),
                RequestMetadata.clientIp(request, properties.security().trustForwardedHeaders()));
    }
}
