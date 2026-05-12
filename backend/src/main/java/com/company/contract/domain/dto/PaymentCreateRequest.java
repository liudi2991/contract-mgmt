package com.company.contract.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentCreateRequest {
    @NotNull
    private Long contractId;

    /**
     * 可选：关联到具体回款期次；为空则按计划日期升序自动核销
     */
    private Long planItemId;

    @NotNull
    private LocalDate receivedDate;

    @NotNull
    @DecimalMin(value = "0.01", message = "实际金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 100)
    private String payer;

    @Size(max = 64)
    private String bankSerial;

    @Size(max = 500)
    private String remark;
}
