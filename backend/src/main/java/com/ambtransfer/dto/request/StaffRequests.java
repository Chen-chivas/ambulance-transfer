package com.ambtransfer.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class StaffRequests {
    private StaffRequests() {}
    public record Create(@NotBlank @Size(max = 50) String name,
                         @NotBlank @Pattern(regexp = "[0-9+() -]{5,30}") String phone,
                         @NotNull @Pattern(regexp = "DRIVER|NURSE") String role,
                         @NotBlank @Size(max = 100) String certNo,
                         @NotNull LocalDate certExpireAt,
                         @Size(max = 50) String username,
                         @Size(max = 72) String password) {}

    public record Update(@NotBlank @Size(max = 50) String name,
                         @NotBlank @Pattern(regexp = "[0-9+() -]{5,30}") String phone,
                         @NotBlank @Size(max = 100) String certNo,
                         @NotNull LocalDate certExpireAt,
                         @NotNull Boolean enabled) {}
}
