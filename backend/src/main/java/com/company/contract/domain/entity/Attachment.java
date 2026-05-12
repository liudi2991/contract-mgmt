package com.company.contract.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {
    private Long id;
    private String ownerType;
    private Long ownerId;
    private String fileName;
    private String objectKey;
    private String contentType;
    private Long size;
    private String md5;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;

    @TableLogic
    private Integer deleted;

    public static final String OWNER_CONTRACT = "CONTRACT";
    public static final String OWNER_PAYMENT = "PAYMENT";
}
