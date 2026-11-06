package com.ambtransfer.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class OrderRequests {
    private OrderRequests() {}

    public record Create(@NotBlank @Size(max = 50) String userName,
                         @NotBlank @Pattern(regexp = "[0-9+() -]{5,30}") String userPhone,
                         @NotBlank @Size(max = 255) String originAddress,
                         @NotBlank @Size(max = 255) String destAddress,
                         @NotNull @Pattern(regexp = "BASIC|MONITORING") String serviceLevel,
                         @Size(max = 1000) String patientNote) {}

    public record Bid(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                      @Size(max = 500) String remark) {}

    public record Select(@Positive long quoteId,
                         @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
}
