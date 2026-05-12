package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("export_record")
public class ExportRecord {
    private Long id;
    private Long userId;
    private String ip;
    private String exportType;
    private String filterJson;
    private Integer rowCount;
    private String fileMd5;
    private String fileName;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
