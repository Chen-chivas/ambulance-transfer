package com.ambtransfer.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class Requests {
    private Requests() {}

    public record Login(@NotBlank @Size(max = 50) String username,
                        @NotBlank @Size(max = 72) String password) {}

    public record Company(@NotBlank @Size(max = 100) String name,
                          @NotBlank @Size(max = 100) String licenseNo,
                          LocalDate licenseExpireAt,
                          @NotBlank @Pattern(regexp = "[0-9+() -]{5,30}") String contactPhone) {}

    public record Audit(@NotNull @Pattern(regexp = "APPROVED|REJECTED") String status,
                        @Size(max = 500) String remark) {}

    public record VehicleInput(@NotBlank @Size(max = 20) String plateNo,
                               @NotNull @Pattern(regexp = "BASIC|MONITORING") String serviceLevel,
                               @NotNull @Pattern(regexp = "IDLE|MAINTENANCE") String status) {}

    public record Dispatcher(@NotBlank @Size(max = 50) String name,
                             @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
                             @NotBlank @Size(min = 10, max = 72) String password,
                             @NotBlank @Pattern(regexp = "[0-9+() -]{5,30}") String phone) {}
}
