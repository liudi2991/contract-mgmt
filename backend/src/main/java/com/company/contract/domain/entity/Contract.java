package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("contract")
public class Contract {
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
    private String status;
    private String voidReason;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableLogic
    private Integer deleted;

    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_VOIDED = "VOIDED";
}
