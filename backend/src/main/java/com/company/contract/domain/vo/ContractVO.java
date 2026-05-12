package com.company.contract.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ContractVO {
    private Long id;
    private String contractNo;
    private String name;
    private String customerName;
    private BigDecimal amount;
    private String currency;
    private LocalDate signDate;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Long ownerId;
    private String ownerName;
    private String status;
    private String voidReason;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
