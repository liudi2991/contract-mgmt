package com.company.contract.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PaymentPlanGenerateRequest {

    /**
     * MANUAL = 手工录入完整列表；RATIO = 按比例自动生成；UNIFORM = 等额均分
     */
    @NotNull
    private GenerateMode mode;

    /**
     * MANUAL 模式必填，按期次列表填写
     */
    @Size(max = 100)
    private List<PlanItem> items;

    /**
     * RATIO / UNIFORM 模式：分期数（1 - 100）
     */
    private Integer periods;

    /**
     * RATIO 模式：每期百分比，长度必须 = periods，所有项相加 = 100
     */
    private List<BigDecimal> ratios;

    /**
     * RATIO / UNIFORM 模式：相邻期次间隔天数（默认 30）
     */
    private Integer intervalDays;

    /**
     * RATIO / UNIFORM 模式：第一期日期（默认合同签订日 + intervalDays）
     */
    private LocalDate firstDate;

    public enum GenerateMode {
        MANUAL, RATIO, UNIFORM
    }

    @Data
    public static class PlanItem {
        @NotNull
        private Integer periodNo;
        @NotNull
        private LocalDate plannedDate;
        @NotNull
        private BigDecimal plannedAmount;
        private String remark;
    }
}
