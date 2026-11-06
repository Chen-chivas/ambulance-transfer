package com.ambtransfer.dto.response;

import com.ambtransfer.entity.Staff;
import java.time.LocalDate;

public record StaffSummary(Long id, Long companyId, String name, String phone, String role,
                           String username, boolean enabled, String certNo, LocalDate certExpireAt, boolean busy) {
    public static StaffSummary from(Staff value) {
        return new StaffSummary(value.id, value.companyId, value.name, value.phone, value.role, value.username,
            value.enabled, value.certNo, value.certExpireAt, value.busy);
    }
}
