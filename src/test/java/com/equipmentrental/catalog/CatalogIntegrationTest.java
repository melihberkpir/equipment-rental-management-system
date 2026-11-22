package com.equipmentrental.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.User;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.ReservationRepository;
import com.equipmentrental.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
        "app.bootstrap.admin.email=catalog-admin@test.example",
        "app.bootstrap.admin.password=admin-test-password"
})
@AutoConfigureMockMvc
@Testcontainers
class CatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    EquipmentRepository equipment;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    UserRepository users;

    String adminToken;
    String customerToken;

    @BeforeEach
    void signIn() throws Exception {
        adminToken = token(json(post("/api/auth/login"), null,
                "{\"email\":\"catalog-admin@test.example\",\"password\":\"admin-test-password\"}"));
        customerToken = token(json(post("/api/auth/register"), null, """
                {"fullName":"Customer","email":"customer-%s@test.example","password":"customer-password"}
                """.formatted(UUID.randomUUID())));
    }

    @Test
    void writesRequireAdminRole() throws Exception {
        String body = "{\"name\":\"Forbidden\"}";
        json(post("/api/categories"), null, body).andExpect(status().isUnauthorized());
        json(post("/api/categories"), customerToken, body).andExpect(status().isForbidden());
        json(post("/api/equipment"), customerToken, "{}").andExpect(status().isForbidden());
        json(post("/api/equipment/1/maintenance"), customerToken, "{}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/equipment/1").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void readsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        authGet("/api/equipment", customerToken).andExpect(status().isOk());
        authGet("/api/categories", customerToken).andExpect(status().isOk());
    }

    @Test
    void adminManagesEquipmentLifecycle() throws Exception {
        long categoryId = createCategory();
        long id = createEquipment(categoryId, "Excavator", "150.50");

        authGet("/api/equipment/" + id, customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.id").value(categoryId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        json(put("/api/equipment/" + id), adminToken, equipmentBody(categoryId, "Mini Excavator", "99.99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mini Excavator"))
                .andExpect(jsonPath("$.dailyRate").value(99.99));

        mvc.perform(delete("/api/equipment/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        authGet("/api/equipment/" + id, adminToken).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidEquipment() throws Exception {
        long categoryId = createCategory();
        json(post("/api/equipment"), adminToken, equipmentBody(categoryId, "Bad", "-1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dailyRate").exists());
        json(post("/api/equipment"), adminToken, equipmentBody(999999, "Bad", "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsWithFilterSortAndLimit() throws Exception {
        long categoryId = createCategory();
        createEquipment(categoryId, "Alpha Drill", "30.00");
        createEquipment(categoryId, "Beta Drill", "10.00");
        createEquipment(categoryId, "Gamma Saw", "20.00");

        authGet("/api/equipment?categoryId=" + categoryId + "&q=drill&sort=dailyRate,desc&limit=1", customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Alpha Drill")))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        authGet("/api/equipment?categoryId=" + categoryId + "&minRate=15&maxRate=25", customerToken)
                .andExpect(jsonPath("$.items[*].name", contains("Gamma Saw")));
        authGet("/api/equipment?categoryId=" + categoryId + "&q=%25", customerToken)
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void rejectsInvalidPagingAndSorting() throws Exception {
        authGet("/api/equipment?limit=0", customerToken).andExpect(status().isBadRequest());
        authGet("/api/equipment?limit=101", customerToken).andExpect(status().isBadRequest());
        authGet("/api/equipment?sort=passwordHash,asc", customerToken).andExpect(status().isBadRequest());
        authGet("/api/equipment?sort=name,sideways", customerToken).andExpect(status().isBadRequest());
        authGet("/api/equipment?status=UNKNOWN", customerToken).andExpect(status().isBadRequest());
    }

    @Test
    void categoryRulesAreEnforced() throws Exception {
        String name = "Category " + UUID.randomUUID();
        json(post("/api/categories"), adminToken, "{\"name\":\"%s\"}".formatted(name))
                .andExpect(status().isCreated());
        json(post("/api/categories"), adminToken, "{\"name\":\"%s\"}".formatted(name.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(status().isConflict());

        long categoryId = createCategory();
        createEquipment(categoryId, "Held", "1.00");
        mvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void availabilityReflectsReservationsWithExclusiveEndDate() throws Exception {
        long id = createEquipment(createCategory(), "Generator", "40.00");
        saveReservation(id, "2030-03-10", "2030-03-13");

        availability(id, "2030-03-12", "2030-03-14")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasons", hasItem("ALREADY_RESERVED")));
        availability(id, "2030-03-13", "2030-03-15").andExpect(jsonPath("$.available").value(true));
        availability(id, "2030-03-08", "2030-03-10").andExpect(jsonPath("$.available").value(true));
        availability(id, "2030-03-14", "2030-03-14").andExpect(status().isBadRequest());
    }

    @Test
    void availabilityReflectsEquipmentStatus() throws Exception {
        long categoryId = createCategory();
        long id = createEquipment(categoryId, "Mixer", "25.00");
        json(put("/api/equipment/" + id), adminToken, """
                {"categoryId":%d,"name":"Mixer","dailyRate":25.00,"status":"INACTIVE"}
                """.formatted(categoryId)).andExpect(status().isOk());

        availability(id, "2030-05-01", "2030-05-02")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasons", hasItem("EQUIPMENT_NOT_ACTIVE")));
    }

    @Test
    void maintenanceBlocksAvailabilityUntilCompleted() throws Exception {
        long id = createEquipment(createCategory(), "Lift", "60.00");
        String response = json(post("/api/equipment/" + id + "/maintenance"), adminToken, """
                {"description":"Hydraulic service","startDate":"2030-06-10","endDate":"2030-06-12"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        long maintenanceId = ((Number) JsonPath.read(response, "$.id")).longValue();

        availability(id, "2030-06-12", "2030-06-13")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasons", hasItem("UNDER_MAINTENANCE")));
        availability(id, "2030-06-13", "2030-06-14").andExpect(jsonPath("$.available").value(true));

        json(put("/api/equipment/" + id + "/maintenance/" + maintenanceId), adminToken, """
                {"description":"Hydraulic service","startDate":"2030-06-10","endDate":"2030-06-12","status":"COMPLETED"}
                """).andExpect(status().isOk());
        availability(id, "2030-06-12", "2030-06-13").andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void openEndedMaintenanceBlocksLaterDates() throws Exception {
        long id = createEquipment(createCategory(), "Compressor", "35.00");
        json(post("/api/equipment/" + id + "/maintenance"), adminToken, """
                {"description":"Awaiting parts","startDate":"2030-07-01","status":"IN_PROGRESS"}
                """).andExpect(status().isCreated());

        availability(id, "2031-01-01", "2031-01-02").andExpect(jsonPath("$.available").value(false));
        availability(id, "2030-06-29", "2030-07-01").andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void maintenanceCannotOverlapActiveReservation() throws Exception {
        long id = createEquipment(createCategory(), "Crane", "500.00");
        saveReservation(id, "2030-08-10", "2030-08-13");

        json(post("/api/equipment/" + id + "/maintenance"), adminToken, """
                {"description":"Overlap","startDate":"2030-08-12","endDate":"2030-08-12"}
                """).andExpect(status().isConflict());
        json(post("/api/equipment/" + id + "/maintenance"), adminToken, """
                {"description":"Return day","startDate":"2030-08-13","endDate":"2030-08-13"}
                """).andExpect(status().isCreated());
        json(post("/api/equipment/" + id + "/maintenance"), adminToken, """
                {"description":"Invalid","startDate":"2030-09-02","endDate":"2030-09-01"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void equipmentWithHistoryCannotBeDeleted() throws Exception {
        long id = createEquipment(createCategory(), "Trailer", "45.00");
        saveReservation(id, "2030-10-01", "2030-10-02");

        mvc.perform(delete("/api/equipment/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void apiDocsIncludeCatalogEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/equipment'].get").exists())
                .andExpect(jsonPath("$.paths['/api/categories'].post").exists())
                .andExpect(jsonPath("$.paths['/api/equipment/{id}/availability'].get").exists())
                .andExpect(jsonPath("$.paths['/api/equipment/{equipmentId}/maintenance'].post").exists());
    }

    private void saveReservation(long equipmentId, String start, String end) {
        User user = users.findByEmailWithRole("catalog-admin@test.example").orElseThrow();
        Equipment item = equipment.findById(equipmentId).orElseThrow();
        reservations.save(new Reservation(user, item, LocalDate.parse(start), LocalDate.parse(end), BigDecimal.TEN));
    }

    private ResultActions availability(long id, String start, String end) throws Exception {
        return authGet("/api/equipment/%d/availability?startDate=%s&endDate=%s".formatted(id, start, end),
                customerToken);
    }

    private long createCategory() throws Exception {
        return id(json(post("/api/categories"), adminToken,
                "{\"name\":\"Category %s\"}".formatted(UUID.randomUUID())).andExpect(status().isCreated()));
    }

    private long createEquipment(long categoryId, String name, String rate) throws Exception {
        return id(json(post("/api/equipment"), adminToken, equipmentBody(categoryId, name, rate))
                .andExpect(status().isCreated()));
    }

    private static String equipmentBody(long categoryId, String name, String rate) {
        return "{\"categoryId\":%d,\"name\":\"%s\",\"dailyRate\":%s}".formatted(categoryId, name, rate);
    }

    private ResultActions authGet(String path, String token) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
