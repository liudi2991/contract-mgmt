package com.company.contract.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserCreateRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名 3-50 位，仅允许字母数字下划线")
    private String username;

    @NotBlank
    private String name;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank
    @Pattern(regexp = "^(SALES|ADMIN)$", message = "角色必须是 SALES 或 ADMIN")
    private String role;
}
