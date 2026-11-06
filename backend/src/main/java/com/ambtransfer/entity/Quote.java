package com.ambtransfer.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Quote {
    public Long id;
    public Long orderId;
    public Long companyId;
    public String companyName;
    public BigDecimal amount;
    public String remark;
    public String status;
    public boolean eligible;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
