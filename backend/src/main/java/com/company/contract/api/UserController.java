package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.PageResult;
import com.company.contract.common.Result;
import com.company.contract.domain.dto.UserCreateRequest;
import com.company.contract.domain.dto.UserUpdateRequest;
import com.company.contract.domain.vo.UserVO;
import com.company.contract.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(page, size, keyword));
    }

    @Operation(summary = "全部启用用户（用于负责人下拉）")
    @GetMapping("/options")
    public Result<List<UserVO>> options() {
        return Result.ok(userService.listAll());
    }

    @Operation(summary = "新建用户")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(module = "USER", action = "CREATE", targetType = "User")
    public Result<Map<String, String>> create(@Valid @RequestBody UserCreateRequest req) {
        return Result.ok(userService.create(req));
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(module = "USER", action = "UPDATE", targetType = "User")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        userService.update(id, req);
        return Result.ok();
    }

    @Operation(summary = "停用用户")
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(module = "USER", action = "DISABLE", targetType = "User")
    public Result<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(module = "USER", action = "RESET_PASSWORD", targetType = "User")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        return Result.ok(userService.resetPassword(id));
    }
}
