package com.company.contract.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentPlanItemVO {
    private Long id;
    private Long contractId;
    private Integer periodNo;
    private LocalDate plannedDate;
    private BigDecimal plannedAmount;
    private BigDecimal receivedAmount;
    private String status;
    private String remark;
}
