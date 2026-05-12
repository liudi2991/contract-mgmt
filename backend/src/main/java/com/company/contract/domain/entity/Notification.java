package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String targetUrl;
    private Integer isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
