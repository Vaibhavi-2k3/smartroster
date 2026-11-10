package com.smartroster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroster.Models.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RosterRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RosterRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ---- employees ----
    public List<Employee> employees() {
        Map<Long, List<Slot>> blocked = new HashMap<>();
        jdbc.query("SELECT employee_id, day, shift FROM unavailability ORDER BY employee_id, day, shift",
                (RowCallbackHandler) rs -> blocked
                        .computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new Slot(rs.getInt(2), rs.getInt(3))));
        return jdbc.query("SELECT id, name, hourly_rate, max_shifts FROM employees ORDER BY id",
                (rs, i) -> new Employee(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getInt(4),
                        blocked.getOrDefault(rs.getLong(1), List.of())));
    }

    @Transactional
    public long addEmployee(Employee e) {
        Long id = jdbc.queryForObject(
                "INSERT INTO employees (name, hourly_rate, max_shifts) VALUES (?, ?, ?) RETURNING id",
                Long.class, e.name(), e.hourlyRate(), e.maxShifts());
        if (e.unavailable() != null) {
            for (Slot s : e.unavailable()) {
                jdbc.update("INSERT INTO unavailability (employee_id, day, shift) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        id, s.day(), s.shift());
            }
        }
        return id;
    }

    public void deleteEmployee(long id) {
        jdbc.update("DELETE FROM employees WHERE id = ?", id);
    }

    // ---- demand ----
    public List<Demand> demand() {
        return jdbc.query("SELECT day, shift, required FROM demand ORDER BY day, shift",
                (rs, i) -> new Demand(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
    }

    @Transactional
    public void saveDemand(List<Demand> rows) {
        for (Demand d : rows) {
            jdbc.update("""
                    INSERT INTO demand (day, shift, required) VALUES (?, ?, ?)
                    ON CONFLICT (day, shift) DO UPDATE SET required = EXCLUDED.required""",
                    d.day(), d.shift(), d.required());
        }
    }

    // ---- schedules ----
    public long createSchedule() {
        return jdbc.queryForObject("INSERT INTO schedules DEFAULT VALUES RETURNING id", Long.class);
    }

    public void completeSchedule(long id, String status, double cost, String resultJson) {
        jdbc.update("UPDATE schedules SET status = ?, cost = ?, result = ?::jsonb WHERE id = ?",
                status, cost, resultJson, id);
    }

    public void failSchedule(long id, String error) {
        jdbc.update("UPDATE schedules SET status = 'FAILED', error = ? WHERE id = ?", error, id);
    }

    public Optional<Schedule> schedule(long id) {
        return jdbc.query(SCHEDULE_SQL + " WHERE id = ?", (rs, i) -> mapSchedule(rs), id).stream().findFirst();
    }

    public Optional<Schedule> latestSchedule() {
        return jdbc.query(SCHEDULE_SQL + " ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, i) -> mapSchedule(rs)).stream().findFirst();
    }

    private static final String SCHEDULE_SQL =
            "SELECT id, status, cost, created_at::text, result::text, error FROM schedules";

    private Schedule mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            String raw = rs.getString(5);
            JsonNode result = raw == null ? null : mapper.readTree(raw);
            double cost = rs.getDouble(3);
            return new Schedule(rs.getLong(1), rs.getString(2), rs.wasNull() ? null : cost,
                    rs.getString(4), result, rs.getString(6));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
