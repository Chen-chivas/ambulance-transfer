package com.ambtransfer.security;

import com.ambtransfer.entity.Staff;

public record UserPrincipal(Long id, Long companyId, String name, String role, String username) {
    public static UserPrincipal from(Staff staff) {
        return new UserPrincipal(staff.id, staff.companyId, staff.name, staff.role, staff.username);
    }
}
