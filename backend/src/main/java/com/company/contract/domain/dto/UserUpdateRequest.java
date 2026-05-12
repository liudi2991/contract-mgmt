package com.company.contract.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank
    private String name;

    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^(SALES|ADMIN)$")
    private String role;
}
