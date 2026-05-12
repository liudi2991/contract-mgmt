package com.company.contract.domain.dto;

import com.company.contract.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContractQueryRequest extends PageQuery {
    private String keyword;
    private String customerName;
    private Long ownerId;
    private String status;
    private LocalDate signDateFrom;
    private LocalDate signDateTo;
    private LocalDate expireDateFrom;
    private LocalDate expireDateTo;
}
