package com.bookwell.auth;

import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthFlowIT extends IntegrationTestBase {

    private static final String PASSWORD = "StrongPass!2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationAssignsCustomerRoleAndIgnoresInjectedRole() throws Exception {
        String email = uniqueEmail("register");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Asha Rao",
                                "phone", "+91 90000 11111",
                                "role", "ADMIN",
                                "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        JsonNode body = json(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);

        String refreshCookie = cookieValue(result, "bookwell_refresh");
        assertThat(refreshCookie).isNotBlank();
        assertThat(setCookie(result, "bookwell_refresh"))
                .contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");

        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void providerRegistrationStartsPendingUntilAdminApproves() throws Exception {
        String email = uniqueEmail("provider-register");
        String businessName = "Pending Studio " + email.substring(15, 21);

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Neel Kapoor",
                                "businessName", businessName,
                                "displayName", "Neel K",
                                "category", "Salon & Spa",
                                "city", "Pune",
                                "timeZone", "Asia/Kolkata"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PROVIDER"))
                .andExpect(jsonPath("$.user.providerStatus").value("PENDING"))
                .andReturn();

        String providerId = json(registration).get("user").get("providerId").asText();
        assertThat(providerId).isNotBlank();

        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isNotFound());

        String adminToken = loginToken("admin-" + email, Role.ADMIN);
        mockMvc.perform(patch("/api/v1/admin/providers/{id}/status", providerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider.businessName").value(businessName));
    }

    @Test
    void duplicateWeakAndMalformedRegistrationsAreRejected() throws Exception {
        String email = uniqueEmail("duplicate");
        String validBody = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", PASSWORD, "fullName", "Duplicate User"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", uniqueEmail("weak"), "password", "short", "fullName", "Weak Password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email", "password", PASSWORD, "fullName", "Bad Email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void csrfEndpointEmitsExactlyOneCookieThatMatchesTheJsonToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body.get("headerName").asText()).isEqualTo("X-XSRF-TOKEN");
        String token = body.get("token").asText();
        assertThat(token).isNotBlank();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst()).startsWith("XSRF-TOKEN=" + token + ";");
        assertThat(cookieValue(result, "XSRF-TOKEN")).isEqualTo(token);
    }

    @Test
    void refreshRequiresCsrfRotatesTheTokenAndReuseRevokesTheFamily() throws Exception {
        String email = uniqueEmail("rotate");
        MvcResult registration = register(email);
        String accessToken = json(registration).get("accessToken").asText();
        String firstRefresh = cookieValue(registration, "bookwell_refresh");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("bookwell_refresh", firstRefresh)))
                .andExpect(status().isForbidden());

        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = json(csrf).get("token").asText();

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("bookwell_refresh", firstRefresh),
                                new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefresh = cookieValue(refreshed, "bookwell_refresh");
        assertThat(secondRefresh).isNotBlank().isNotEqualTo(firstRefresh);

        String newAccess = json(refreshed).get("accessToken").asText();
        assertThat(newAccess).isNotBlank().isNotEqualTo(accessToken);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("bookwell_refresh", firstRefresh),
                                new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());

        // Reuse detection revoked the whole token family, including the newest token.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("bookwell_refresh", secondRefresh),
                                new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfClearsTheCookieAndRevokesTheSession() throws Exception {
        String email = uniqueEmail("logout");
        MvcResult registration = register(email);
        String refresh = cookieValue(registration, "bookwell_refresh");

        String csrfToken = json(mockMvc.perform(get("/api/v1/auth/csrf")).andReturn()).get("token").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("bookwell_refresh", refresh)))
                .andExpect(status().isForbidden());

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("bookwell_refresh", refresh),
                                new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(setCookie(logout, "bookwell_refresh")).contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("bookwell_refresh", refresh),
                                new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesRejectAnonymousWrongRoleTamperedAndDisabledUsers() throws Exception {
        String email = uniqueEmail("roles");
        MvcResult registration = register(email);
        String customerToken = json(registration).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk());

        String adminToken = loginToken("roles-admin-" + email, Role.ADMIN);
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").isNumber());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken + "tampered"))
                .andExpect(status().isUnauthorized());

        User disabled = newUser(Role.CUSTOMER);
        String disabledToken = loginTokenFor(disabled);
        inTx(() -> {
            User managed = users.findById(disabled.getId()).orElseThrow();
            managed.setEnabled(false);
            users.saveAndFlush(managed);
        });
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + disabledToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", disabled.getEmail(), "password", TEST_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        User user = newUser(Role.CUSTOMER);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", "WrongPass!2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Test Person",
                                "phone", "+91 90000 22222"))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginToken(String emailPrefix, Role role) throws Exception {
        User user = newUser(role);
        // The generated address is unique; the prefix keeps failures readable.
        assertThat(user.getEmail()).startsWith(role.name().toLowerCase());
        return loginTokenFor(user);
    }

    private String loginTokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(JsonNode body) {
        return "Bearer " + body.get("accessToken").asText();
    }

    private String cookieValue(MvcResult result, String name) {
        String header = setCookie(result, name);
        if (header.isBlank()) {
            return null;
        }
        String value = header.substring(name.length() + 1);
        int separator = value.indexOf(';');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElse("");
    }
}
