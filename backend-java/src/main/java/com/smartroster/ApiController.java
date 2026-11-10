package com.smartroster;

import com.smartroster.Models.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@org.springframework.validation.annotation.Validated
public class ApiController {
    private final RosterRepository repo;
    private final ScheduleService scheduler;

    public ApiController(RosterRepository repo, ScheduleService scheduler) {
        this.repo = repo;
        this.scheduler = scheduler;
    }

    @GetMapping("/employees")
    public List<Employee> employees() {
        return repo.employees();
    }

    @PostMapping("/employees")
    public Map<String, Long> addEmployee(@Valid @RequestBody Employee e) {
        return Map.of("id", repo.addEmployee(e));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable long id) {
        repo.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/demand")
    public List<Demand> demand() {
        return repo.demand();
    }

    @PutMapping("/demand")
    public ResponseEntity<Void> saveDemand(@RequestBody List<@Valid Demand> rows) {
        repo.saveDemand(rows);
        return ResponseEntity.noContent().build();
    }

    /** Starts an optimization job and returns its id; poll GET /api/schedules/{id}. */
    @PostMapping("/schedules")
    public ResponseEntity<Map<String, Long>> generate(@RequestParam(defaultValue = "false") boolean useForecast) {
        return ResponseEntity.accepted().body(Map.of("id", scheduler.submit(useForecast)));
    }

    @GetMapping("/schedules/latest")
    public ResponseEntity<Schedule> latest() {
        return repo.latestSchedule().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/schedules/{id}")
    public ResponseEntity<Schedule> schedule(@PathVariable long id) {
        return repo.schedule(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
