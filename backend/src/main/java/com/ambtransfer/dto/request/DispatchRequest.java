package com.ambtransfer.dto.request;

import jakarta.validation.constraints.*;

public record DispatchRequest(@NotNull @Positive Long vehicleId,
                              @NotNull @Positive Long driverId,
                              @Positive Long nurseId) {}
