package com.ambtransfer.dto.request;

import jakarta.validation.constraints.*;

public class OrderQuery {
    @Min(1) @Max(1000000) public int page = 1;
    @Min(1) @Max(100) public int size = 10;
    @NotNull @Size(max = 100) public String keyword = "";
    @NotNull @Pattern(regexp = "|BASIC|MONITORING") public String serviceLevel = "";
    @NotNull @Pattern(regexp = "|PENDING_QUOTE|QUOTING|CONFIRMED|DISPATCHED|EN_ROUTE|ARRIVED|ON_BOARD|COMPLETED|CANCELLED") public String status = "";
    public long getOffset() { return (long) (page - 1) * size; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
