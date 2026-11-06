package com.ambtransfer.entity;

import java.time.LocalDateTime;

public class OrderStatusLog {
    public Long id;
    public Long orderId;
    public String fromStatus;
    public String toStatus;
    public String operatorType;
    public Long operatorId;
    public String eventType;
    public String remark;
    public LocalDateTime createdAt;
}
