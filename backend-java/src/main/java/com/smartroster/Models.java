package com.smartroster;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.util.List;

/** API / domain objects. Shift: 0 morning, 1 evening, 2 night. Day: 0 Monday .. 6 Sunday. */
public final class Models {
    private Models() {}

    public record Slot(@Min(0) @Max(6) int day, @Min(0) @Max(2) int shift) {}

    public record Employee(Long id,
                           @NotBlank String name,
                           @PositiveOrZero double hourlyRate,
                           @Min(0) @Max(7) int maxShifts,
                           List<Slot> unavailable) {}

    public record Demand(@Min(0) @Max(6) int day, @Min(0) @Max(2) int shift, @Min(0) int required) {}

    public record Schedule(Long id, String status, Double cost, String createdAt,
                           JsonNode result, String error) {}
}
