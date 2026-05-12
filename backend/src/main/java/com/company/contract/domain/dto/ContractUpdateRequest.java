package com.company.contract.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContractUpdateRequest {
    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Size(max = 100)
    private String customerName;

    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private LocalDate signDate;

    @NotNull
    private LocalDate effectiveDate;

    @NotNull
    private LocalDate expireDate;

    @NotNull
    private Long ownerId;

    @Size(max = 500)
    private String remark;
}
