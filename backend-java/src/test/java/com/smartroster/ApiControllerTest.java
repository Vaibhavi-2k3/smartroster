package com.smartroster;

import com.smartroster.Models.Employee;
import com.smartroster.Models.Schedule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
class ApiControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    RosterRepository repo;
    @MockBean
    ScheduleService scheduler;

    @Test
    void getEmployeesReturnsList() throws Exception {
        when(repo.employees()).thenReturn(List.of(new Employee(1L, "Aarav", 18.0, 5, List.of())));
        mvc.perform(get("/api/employees"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Aarav"));
    }

    @Test
    void addEmployeeRejectsBlankName() throws Exception {
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"hourlyRate\":18,\"maxShifts\":5}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void addEmployeeReturnsId() throws Exception {
        when(repo.addEmployee(any(Employee.class))).thenReturn(7L);
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nina\",\"hourlyRate\":20,\"maxShifts\":5,\"unavailable\":[]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void deleteEmployeeReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/employees/3"))
           .andExpect(status().isNoContent());
    }

    @Test
    void generateReturnsAcceptedWithId() throws Exception {
        when(scheduler.submit(true)).thenReturn(42L);
        mvc.perform(post("/api/schedules").param("useForecast", "true"))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void scheduleReturns404WhenMissing() throws Exception {
        when(repo.schedule(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/schedules/1"))
           .andExpect(status().isNotFound());
    }

    @Test
    void scheduleReturnsResultWhenFound() throws Exception {
        Schedule schedule = new Schedule(1L, "OPTIMAL", 100.0, "2026-01-01T00:00:00Z",
                null, null);
        when(repo.schedule(1L)).thenReturn(Optional.of(schedule));
        mvc.perform(get("/api/schedules/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("OPTIMAL"));
    }

    @Test
    void latestReturnsNoContentWhenNone() throws Exception {
        when(repo.latestSchedule()).thenReturn(Optional.empty());
        mvc.perform(get("/api/schedules/latest"))
           .andExpect(status().isNoContent());
    }
}
