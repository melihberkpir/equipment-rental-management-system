package com.equipmentrental.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equipmentrental.domain.User;
import com.equipmentrental.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-123",
        "app.security.jwt.expiration=1h",
        "app.bootstrap.admin.email=admin@test.example",
        "app.bootstrap.admin.password=admin-test-password"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-password";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registerCreatesCustomerAndStoresOnlyPasswordHash() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        User saved = users.findByEmailWithRole(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void registerIgnoresClientSuppliedRole() throws Exception {
        String body = """
                {"fullName":"Test User","email":"%s","password":"%s","role":"ADMIN"}
                """.formatted(uniqueEmail(), PASSWORD);

        postJson("/api/auth/register", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(java.util.Locale.ROOT), PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsPasswordOverBcryptByteLimit() throws Exception {
        register(uniqueEmail(), "ş".repeat(40)).andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        String response = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.accessToken");

        // No controller exists here yet, so 404 proves the request passed authentication.
        mvc.perform(get("/api/protected-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUser() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email, "wrong-password").andExpect(status().isUnauthorized());
        login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/protected-probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/protected-probe").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredAdminCanSignIn() throws Exception {
        login("admin@test.example", "admin-test-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register", """
                {"fullName":"Test User","email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.example";
    }
}
