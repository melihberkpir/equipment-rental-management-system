package com.equipmentrental.agreement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-123",
        "app.security.jwt.expiration=1h",
        "app.bootstrap.admin.email=agreement-admin@test.example",
        "app.bootstrap.admin.password=admin-test-password"
})
@AutoConfigureMockMvc
@Testcontainers
class AgreementIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);
    private static final String CUSTOMER_NAME = "Şükrü Öğüt İnce";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    String adminToken;
    String ownerToken;
    String otherToken;
    long equipmentId;
    int nextDay = 1;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = token(json(post("/api/auth/login"), null,
                "{\"email\":\"agreement-admin@test.example\",\"password\":\"admin-test-password\"}"));
        ownerToken = register(CUSTOMER_NAME);
        otherToken = register("Other Customer");
        long categoryId = id(json(post("/api/categories"), adminToken,
                "{\"name\":\"Category %s\"}".formatted(UUID.randomUUID())));
        equipmentId = id(json(post("/api/equipment"), adminToken,
                "{\"categoryId\":%d,\"name\":\"Hydraulic Lift\",\"dailyRate\":12.25}".formatted(categoryId)));
    }

    @Test
    void ownerDownloadsAgreementWithReservationDetails() throws Exception {
        long reservationId = confirmedReservation();

        byte[] pdf = download(reservationId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rental-agreement-" + reservationId + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains(CUSTOMER_NAME, "Hydraulic Lift", "12.25", "36.75", "CONFIRMED",
                    "Agreement No: " + reservationId, TODAY.toString());
        }
    }

    @Test
    void adminCanDownloadAnyAgreement() throws Exception {
        download(confirmedReservation(), adminToken).andExpect(status().isOk());
    }

    @Test
    void otherCustomersAndAnonymousUsersAreRejected() throws Exception {
        long reservationId = confirmedReservation();

        download(reservationId, otherToken).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/" + reservationId + "/agreement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agreementRequiresConfirmedOrCompletedReservation() throws Exception {
        long pending = id(reserve());
        download(pending, ownerToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());

        setStatus(pending, ownerToken, "CANCELLED");
        download(pending, ownerToken).andExpect(status().isConflict());
    }

    @Test
    void missingReservationReturnsNotFound() throws Exception {
        download(999999, ownerToken).andExpect(status().isNotFound());
    }

    private long confirmedReservation() throws Exception {
        long id = id(reserve());
        setStatus(id, adminToken, "CONFIRMED");
        return id;
    }

    private ResultActions reserve() throws Exception {
        int offset = nextDay;
        nextDay += 3;
        return json(post("/api/reservations"), ownerToken, """
                {"equipmentId":%d,"startDate":"%s","endDate":"%s"}
                """.formatted(equipmentId, TODAY.plusDays(offset), TODAY.plusDays(offset + 3)))
                .andExpect(status().isCreated());
    }

    private void setStatus(long id, String token, String value) throws Exception {
        json(patch("/api/reservations/" + id + "/status"), token, "{\"status\":\"%s\"}".formatted(value))
                .andExpect(status().isOk());
    }

    private ResultActions download(long id, String token) throws Exception {
        return mvc.perform(get("/api/reservations/" + id + "/agreement")
                .accept(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String register(String name) throws Exception {
        return token(json(post("/api/auth/register"), null,
                "{\"fullName\":\"%s\",\"email\":\"user-%s@test.example\",\"password\":\"customer-password\"}"
                        .formatted(name, UUID.randomUUID())));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8).content(body));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
