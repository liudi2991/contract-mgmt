package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_param")
public class SysParam {
    private Long id;
    private String paramKey;
    private String paramValue;
    private String description;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
