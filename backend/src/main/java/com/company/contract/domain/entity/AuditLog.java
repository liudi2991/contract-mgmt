package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeJson;
    private String afterJson;
    private String result;
    private String errorMsg;
    private LocalDateTime createdAt;
}
