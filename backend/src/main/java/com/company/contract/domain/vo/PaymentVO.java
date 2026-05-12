package com.company.contract.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaymentVO {
    private Long id;
    private Long contractId;
    private String contractNo;
    private String customerName;
    private Long planItemId;
    private LocalDate receivedDate;
    private BigDecimal amount;
    private String payer;
    private String bankSerial;
    private String remark;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    /**
     * 本次核销明细（仅在创建/更新返回时填充）
     */
    private List<SettlementVO> settlements;
    private BigDecimal contractPaidAmount;
    private BigDecimal contractRemainingAmount;

    @Data
    @Builder
    public static class SettlementVO {
        private Long planItemId;
        private Integer periodNo;
        private BigDecimal settledAmount;
        private String planItemStatus;
    }
}
