package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.Result;
import com.company.contract.domain.entity.SysParam;
import com.company.contract.service.sysparam.SysParamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统参数")
@RestController
@RequestMapping("/api/v1/sys-params")
@RequiredArgsConstructor
public class SysParamController {

    private final SysParamService sysParamService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysParam>> list() {
        return Result.ok(sysParamService.listAll());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(module = "SYS_PARAM", action = "UPDATE")
    public Result<Void> update(@PathVariable("key") String key, @RequestBody UpdateRequest req) {
        sysParamService.update(key, req.getValue());
        return Result.ok();
    }

    @Data
    public static class UpdateRequest {
        private String value;
    }
}
