package com.smartroster;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroster.Models.Demand;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ScheduleRunner {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration OPTIMIZER_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ML_TIMEOUT = Duration.ofSeconds(30);

    private final RosterRepository repo;
    private final RestClient optimizer;
    private final RestClient ml;

    public ScheduleRunner(RosterRepository repo,
                          @Value("${optimizer.url}") String optimizerUrl,
                          @Value("${ml.url}") String mlUrl) {
        this.repo = repo;
        this.optimizer = client(optimizerUrl, OPTIMIZER_TIMEOUT);
        this.ml = client(mlUrl, ML_TIMEOUT);
    }

    @Async
    public void run(long id, boolean useForecast) {
        try {
            List<Demand> demand = useForecast
                    ? ml.get().uri("/forecast").retrieve().body(new ParameterizedTypeReference<List<Demand>>() {})
                    : repo.demand();
            Map<String, Object> request = Map.of("employees", repo.employees(), "demand", demand);
            JsonNode result = optimizer.post().uri("/solve")
                    .contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve().body(JsonNode.class);
            if (useForecast) {
                repo.saveDemand(demand);
            }
            repo.completeSchedule(id, result.path("status").asText("FEASIBLE"),
                    result.path("cost").asDouble(), result.toString());
        } catch (Exception e) {
            repo.failSchedule(id, String.valueOf(e.getMessage()));
        }
    }

    private static RestClient client(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
