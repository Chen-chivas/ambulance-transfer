package com.ambtransfer.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CarCompany {
    public Long id;
    public String name;
    public String licenseNo;
    public LocalDate licenseExpireAt;
    public String auditStatus;
    public String auditRemark;
    public String contactPhone;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
