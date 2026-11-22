package com.equipmentrental.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.equipmentrental.domain.Equipment;
import com.equipmentrental.domain.Reservation;
import com.equipmentrental.domain.ReservationStatus;
import com.equipmentrental.domain.User;
import com.equipmentrental.repository.EquipmentRepository;
import com.equipmentrental.repository.ReservationRepository;
import com.equipmentrental.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        "app.bootstrap.admin.email=reservation-admin@test.example",
        "app.bootstrap.admin.password=admin-test-password"
})
@AutoConfigureMockMvc
@Testcontainers
class ReservationIntegrationTest {

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
    String otherCustomerToken;
    String customerEmail;

    @BeforeEach
    void signIn() throws Exception {
        adminToken = token(json(post("/api/auth/login"), null,
                "{\"email\":\"reservation-admin@test.example\",\"password\":\"admin-test-password\"}"));
        customerEmail = "customer-" + UUID.randomUUID() + "@test.example";
        customerToken = registerCustomer(customerEmail);
        otherCustomerToken = registerCustomer("other-" + UUID.randomUUID() + "@test.example");
    }

    @Test
    void createsReservationAndCalculatesTotal() throws Exception {
        long equipmentId = createEquipment("12.25");

        reserve(customerToken, equipmentId, day(10), day(13))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rentalDays").value(3))
                .andExpect(jsonPath("$.totalCost").value(36.75));
    }

    @Test
    void ignoresClientSuppliedTotal() throws Exception {
        long equipmentId = createEquipment("10.00");
        json(post("/api/reservations"), customerToken, """
                {"equipmentId":%d,"startDate":"%s","endDate":"%s","totalCost":0.01}
                """.formatted(equipmentId, day(5), day(7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(20.00));
    }

    @Test
    void rejectsOverlapAndAllowsSameDayTurnover() throws Exception {
        long equipmentId = createEquipment("10.00");
        reserve(customerToken, equipmentId, day(10), day(13)).andExpect(status().isCreated());

        reserve(otherCustomerToken, equipmentId, day(12), day(15))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("ALREADY_RESERVED")));
        reserve(otherCustomerToken, equipmentId, day(13), day(15)).andExpect(status().isCreated());
    }

    @Test
    void concurrentOverlappingRequestsCreateOnlyOneReservation() throws Exception {
        long equipmentId = createEquipment("10.00");
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return reserve(customerToken, equipmentId, day(20), day(22))
                            .andReturn().getResponse().getStatus();
                };
                results.add(executor.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidDatesAndUnavailableEquipment() throws Exception {
        long equipmentId = createEquipment("10.00");
        reserve(customerToken, equipmentId, day(5), day(5)).andExpect(status().isBadRequest());
        reserve(customerToken, equipmentId, day(-2), day(1)).andExpect(status().isBadRequest());
        reserve(customerToken, 999999, day(1), day(2)).andExpect(status().isNotFound());

        json(post("/api/equipment/" + equipmentId + "/maintenance"), adminToken, """
                {"description":"Service","startDate":"%s","endDate":"%s"}
                """.formatted(day(30), day(31))).andExpect(status().isCreated());
        reserve(customerToken, equipmentId, day(31), day(33))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("UNDER_MAINTENANCE")));

        long inactiveId = createEquipment("10.00");
        setEquipmentStatus(inactiveId, "INACTIVE");
        reserve(customerToken, inactiveId, day(1), day(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("EQUIPMENT_NOT_ACTIVE")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        json(post("/api/reservations"), null, "{}").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    void customersAccessOnlyTheirOwnReservations() throws Exception {
        long equipmentId = createEquipment("10.00");
        long own = id(reserve(customerToken, equipmentId, day(1), day(2)));
        long other = id(reserve(otherCustomerToken, equipmentId, day(3), day(4)));

        authGet("/api/reservations/" + own, customerToken).andExpect(status().isOk());
        authGet("/api/reservations/" + other, customerToken).andExpect(status().isForbidden());
        setStatus(other, customerToken, "CANCELLED").andExpect(status().isForbidden());
        authGet("/api/reservations/" + other, adminToken).andExpect(status().isOk());

        authGet("/api/reservations", customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[*].id", contains((int) own)));
        String all = authGet("/api/reservations?limit=100", adminToken)
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(all, "$.items[*].id");
        assertThat(ids).contains((int) own, (int) other);
    }

    @Test
    void customerCanCancelAndDatesBecomeAvailable() throws Exception {
        long equipmentId = createEquipment("10.00");
        long reservationId = id(reserve(customerToken, equipmentId, day(5), day(8)));

        setStatus(reservationId, customerToken, "CONFIRMED").andExpect(status().isForbidden());
        setStatus(reservationId, customerToken, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        setStatus(reservationId, customerToken, "CANCELLED").andExpect(status().isConflict());

        reserve(otherCustomerToken, equipmentId, day(5), day(8)).andExpect(status().isCreated());
    }

    @Test
    void customerCannotCancelAfterStartDate() throws Exception {
        long equipmentId = createEquipment("10.00");
        long reservationId = saveReservation(equipmentId, day(0), day(2), ReservationStatus.CONFIRMED);

        setStatus(reservationId, customerToken, "CANCELLED").andExpect(status().isConflict());
        setStatus(reservationId, adminToken, "CANCELLED").andExpect(status().isOk());
    }

    @Test
    void adminFollowsValidStatusTransitions() throws Exception {
        long equipmentId = createEquipment("10.00");
        long future = id(reserve(customerToken, equipmentId, day(5), day(6)));

        setStatus(future, adminToken, "COMPLETED").andExpect(status().isConflict());
        setStatus(future, adminToken, "CONFIRMED").andExpect(status().isOk());
        setStatus(future, adminToken, "COMPLETED").andExpect(status().isConflict());
        setStatus(future, adminToken, "PENDING").andExpect(status().isConflict());

        long started = saveReservation(equipmentId, day(-1), day(1), ReservationStatus.CONFIRMED);
        setStatus(started, adminToken, "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        setStatus(started, adminToken, "CANCELLED").andExpect(status().isConflict());
    }

    @Test
    void cannotConfirmWhileEquipmentIsInactive() throws Exception {
        long equipmentId = createEquipment("10.00");
        long reservationId = id(reserve(customerToken, equipmentId, day(5), day(6)));
        setEquipmentStatus(equipmentId, "INACTIVE");

        setStatus(reservationId, adminToken, "CONFIRMED").andExpect(status().isConflict());
    }

    @Test
    void confirmationRecordsPaymentAndCancellationRefundsIt() throws Exception {
        long equipmentId = createEquipment("15.50");
        long reservationId = id(reserve(customerToken, equipmentId, day(5), day(7)));
        String path = "/api/payments?reservationId=" + reservationId;

        authGet(path, adminToken).andExpect(jsonPath("$.totalItems").value(0));

        setStatus(reservationId, adminToken, "CONFIRMED").andExpect(status().isOk());
        authGet(path, adminToken)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].amount").value(31.00));

        setStatus(reservationId, customerToken, "CANCELLED").andExpect(status().isOk());
        authGet(path, adminToken)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].status").value("REFUNDED"));
    }

    @Test
    void paymentsAreAdminOnly() throws Exception {
        authGet("/api/payments", customerToken).andExpect(status().isForbidden());
        authGet("/api/payments/1", customerToken).andExpect(status().isForbidden());
        authGet("/api/payments", adminToken).andExpect(status().isOk());
    }

    private long saveReservation(long equipmentId, String start, String end, ReservationStatus reservationStatus) {
        User user = users.findByEmailWithRole(customerEmail).orElseThrow();
        Equipment item = equipment.findById(equipmentId).orElseThrow();
        Reservation reservation = new Reservation(user, item, LocalDate.parse(start), LocalDate.parse(end),
                BigDecimal.TEN);
        reservation.setStatus(reservationStatus);
        return reservations.save(reservation).getId();
    }

    private ResultActions reserve(String token, long equipmentId, String start, String end) throws Exception {
        return json(post("/api/reservations"), token, """
                {"equipmentId":%d,"startDate":"%s","endDate":"%s"}
                """.formatted(equipmentId, start, end));
    }

    private ResultActions setStatus(long id, String token, String value) throws Exception {
        return json(patch("/api/reservations/" + id + "/status"), token, "{\"status\":\"%s\"}".formatted(value));
    }

    private long createEquipment(String rate) throws Exception {
        long categoryId = id(json(post("/api/categories"), adminToken,
                "{\"name\":\"Category %s\"}".formatted(UUID.randomUUID())));
        return id(json(post("/api/equipment"), adminToken,
                "{\"categoryId\":%d,\"name\":\"Item\",\"dailyRate\":%s}".formatted(categoryId, rate)));
    }

    private void setEquipmentStatus(long id, String value) throws Exception {
        long categoryId = equipment.findWithCategoryById(id).orElseThrow().getCategory().getId();
        json(put("/api/equipment/" + id), adminToken,
                "{\"categoryId\":%d,\"name\":\"Item\",\"dailyRate\":10.00,\"status\":\"%s\"}"
                        .formatted(categoryId, value))
                .andExpect(status().isOk());
    }

    private String registerCustomer(String email) throws Exception {
        return token(json(post("/api/auth/register"), null,
                "{\"fullName\":\"Customer\",\"email\":\"%s\",\"password\":\"customer-password\"}".formatted(email)));
    }

    private ResultActions authGet(String path, String token) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String day(int offset) {
        return LocalDate.now().plusDays(offset).toString();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
