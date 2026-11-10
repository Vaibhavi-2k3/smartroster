package com.smartroster;

import com.smartroster.Models.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
@Rollback
class RosterRepositoryIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Paths.get("../db/init.sql").toAbsolutePath().normalize().toString()),
                    "/docker-entrypoint-initdb.d/init.sql");

    @Autowired
    RosterRepository repo;

    @Test
    void employeesReturnsSeededTeamWithUnavailability() {
        List<Employee> emps = repo.employees();
        assertThat(emps).hasSize(8);
        Employee priya = emps.stream().filter(e -> e.name().equals("Priya")).findFirst().orElseThrow();
        assertThat(priya.unavailable()).hasSize(3);
        assertThat(priya.maxShifts()).isEqualTo(5);
    }

    @Test
    void addAndDeleteEmployee() {
        long id = repo.addEmployee(new Employee(null, "Test", 22.5, 4, List.of(new Slot(2, 1))));
        Employee found = repo.employees().stream().filter(e -> e.id() == id).findFirst().orElseThrow();
        assertThat(found.name()).isEqualTo("Test");
        assertThat(found.unavailable()).contains(new Slot(2, 1));

        repo.deleteEmployee(id);
        assertThat(repo.employees().stream().noneMatch(e -> e.id() == id)).isTrue();
    }

    @Test
    void saveDemandUpsertsRows() {
        repo.saveDemand(List.of(new Demand(0, 0, 7)));
        Demand row = repo.demand().stream()
                .filter(d -> d.day() == 0 && d.shift() == 0).findFirst().orElseThrow();
        assertThat(row.required()).isEqualTo(7);
    }

    @Test
    void createCompleteAndFetchScheduleRoundTripsJsonb() {
        long id = repo.createSchedule();
        String json = "{\"status\":\"OPTIMAL\",\"cost\":123.45,\"assignments\":[{\"employeeId\":1,\"day\":0,\"shift\":0}]}";
        repo.completeSchedule(id, "OPTIMAL", 123.45, json);

        Schedule s = repo.schedule(id).orElseThrow();
        assertThat(s.status()).isEqualTo("OPTIMAL");
        assertThat(s.cost()).isEqualTo(123.45);
        assertThat(s.result()).isNotNull();
        assertThat(s.result().path("assignments").size()).isEqualTo(1);

        Schedule latest = repo.latestSchedule().orElseThrow();
        assertThat(latest.id()).isEqualTo(id);
    }

    @Test
    void failScheduleSetsError() {
        long id = repo.createSchedule();
        repo.failSchedule(id, "boom");
        Schedule s = repo.schedule(id).orElseThrow();
        assertThat(s.status()).isEqualTo("FAILED");
        assertThat(s.error()).isEqualTo("boom");
    }

    @Test
    void scheduleReturnsEmptyWhenMissing() {
        Optional<Schedule> s = repo.schedule(999_999L);
        assertThat(s).isEmpty();
    }
}
