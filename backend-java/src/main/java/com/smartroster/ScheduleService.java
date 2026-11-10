package com.smartroster;

import org.springframework.stereotype.Service;

@Service
public class ScheduleService {
    private final RosterRepository repo;
    private final ScheduleRunner runner;

    public ScheduleService(RosterRepository repo, ScheduleRunner runner) {
        this.repo = repo;
        this.runner = runner;
    }

    public long submit(boolean useForecast) {
        long id = repo.createSchedule();
        runner.run(id, useForecast);
        return id;
    }
}
