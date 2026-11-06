package com.ambtransfer.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferOrder {
    public TransferOrder() {}

    // Response filtering must not mutate an entity held in MyBatis's transaction cache.
    public TransferOrder(TransferOrder source) {
        id=source.id; orderNo=source.orderNo; userName=source.userName; userPhone=source.userPhone;
        originAddress=source.originAddress; destAddress=source.destAddress; serviceLevel=source.serviceLevel;
        patientNote=source.patientNote; status=source.status; assignedCompanyId=source.assignedCompanyId;
        assignedCompanyName=source.assignedCompanyName; assignedVehicleId=source.assignedVehicleId;
        assignedDriverId=source.assignedDriverId; assignedNurseId=source.assignedNurseId;
        assignedPlateNo=source.assignedPlateNo; assignedDriverName=source.assignedDriverName; assignedNurseName=source.assignedNurseName;
        finalAmount=source.finalAmount; createdAt=source.createdAt; updatedAt=source.updatedAt;
        myQuoteId=source.myQuoteId; myAmount=source.myAmount; myQuoteStatus=source.myQuoteStatus;
    }
    public Long id;
    public String orderNo;
    public String userName;
    public String userPhone;
    public String originAddress;
    public String destAddress;
    public String serviceLevel;
    public String patientNote;
    public String status;
    public Long assignedCompanyId;
    public String assignedCompanyName;
    public Long assignedVehicleId;
    public Long assignedDriverId;
    public Long assignedNurseId;
    public String assignedPlateNo;
    public String assignedDriverName;
    public String assignedNurseName;
    public BigDecimal finalAmount;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public Long myQuoteId;
    public BigDecimal myAmount;
    public String myQuoteStatus;
}
