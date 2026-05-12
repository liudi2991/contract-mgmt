package com.company.contract.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String name;
    private String email;
    private String role;
    private Integer status;
    private Boolean mustChangePwd;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
