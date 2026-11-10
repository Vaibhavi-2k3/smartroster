package com.smartroster;

import com.smartroster.Models.Demand;
import com.smartroster.Models.Schedule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ScheduleServiceIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Paths.get("../db/init.sql").toAbsolutePath().normalize().toString()),
                    "/docker-entrypoint-initdb.d/init.sql");

    static HttpServer stub;

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stub.createContext("/solve", new SolveHandler());
            stub.createContext("/forecast", new ForecastHandler());
            stub.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + stub.getAddress().getPort();
        registry.add("optimizer.url", () -> base);
        registry.add("ml.url", () -> base);
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @Autowired
    ScheduleService scheduler;
    @Autowired
    RosterRepository repo;

    @Test
    void manualDemandJobCompletesAndPersistsResult() {
        long id = scheduler.submit(false);
        Schedule s = awaitCompletion(id);
        assertThat(s.status()).isEqualTo("OPTIMAL");
        assertThat(s.cost()).isEqualTo(100.0);
        assertThat(s.result().path("assignments").size()).isEqualTo(1);
    }

    @Test
    void forecastJobPersistsForecastDemand() {
        long id = scheduler.submit(true);
        Schedule s = awaitCompletion(id);
        assertThat(s.status()).isEqualTo("OPTIMAL");
        List<Demand> demand = repo.demand();
        assertThat(demand).hasSize(21);
        assertThat(demand.stream().mapToInt(Demand::required).sum()).isEqualTo(21 * 3);
    }

    private Schedule awaitCompletion(long id) {
        for (int i = 0; i < 200; i++) {
            Schedule s = repo.schedule(id).orElseThrow();
            if (!"PENDING".equals(s.status())) {
                return s;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new IllegalStateException("schedule " + id + " did not complete in time");
    }

    static final String SOLVE_JSON = """
            {"status":"OPTIMAL","cost":100.0,
             "assignments":[{"employeeId":1,"name":"Aarav","day":0,"shift":0}],
             "shortfalls":[],
             "stats":{"shiftsPerEmployee":{"Aarav":1},"nightsPerEmployee":{"Aarav":0},"solveSeconds":0.01}}
            """;

    static final class SolveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            byte[] body = SOLVE_JSON.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    static final class ForecastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            for (int d = 0; d < 7; d++) {
                for (int s = 0; s < 3; s++) {
                    if (d != 0 || s != 0) {
                        sb.append(",");
                    }
                    sb.append("{\"day\":").append(d).append(",\"shift\":").append(s)
                      .append(",\"required\":3}");
                }
            }
            sb.append("]");
            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
