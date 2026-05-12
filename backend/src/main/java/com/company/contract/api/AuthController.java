package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.Result;
import com.company.contract.domain.dto.ChangePasswordRequest;
import com.company.contract.domain.dto.LoginRequest;
import com.company.contract.domain.vo.LoginResponse;
import com.company.contract.domain.vo.UserVO;
import com.company.contract.security.JwtAuthenticationFilter;
import com.company.contract.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    @Audit(module = "AUTH", action = "LOGIN")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    @Audit(module = "AUTH", action = "LOGOUT")
    public Result<Void> logout(HttpServletRequest req) {
        String header = req.getHeader(JwtAuthenticationFilter.HEADER);
        if (header != null && header.startsWith(JwtAuthenticationFilter.PREFIX)) {
            authService.logout(header.substring(JwtAuthenticationFilter.PREFIX.length()));
        }
        return Result.ok();
    }

    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    @Audit(module = "AUTH", action = "CHANGE_PASSWORD")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return Result.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }
}
