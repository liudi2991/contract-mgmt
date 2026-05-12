package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.PageResult;
import com.company.contract.common.Result;
import com.company.contract.domain.dto.ContractCreateRequest;
import com.company.contract.domain.dto.ContractQueryRequest;
import com.company.contract.domain.dto.ContractUpdateRequest;
import com.company.contract.domain.vo.ContractVO;
import com.company.contract.service.contract.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "合同")
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @Operation(summary = "新建合同")
    @PostMapping
    @Audit(module = "CONTRACT", action = "CREATE", targetType = "Contract")
    public Result<ContractVO> create(@Valid @RequestBody ContractCreateRequest req) {
        return Result.ok(contractService.create(req));
    }

    @Operation(summary = "编辑合同")
    @PutMapping("/{id}")
    @Audit(module = "CONTRACT", action = "UPDATE", targetType = "Contract")
    public Result<ContractVO> update(@PathVariable Long id, @Valid @RequestBody ContractUpdateRequest req) {
        return Result.ok(contractService.update(id, req));
    }

    @Operation(summary = "作废合同")
    @PostMapping("/{id}/void")
    @Audit(module = "CONTRACT", action = "VOID", targetType = "Contract")
    public Result<Void> voidContract(@PathVariable Long id, @RequestBody VoidRequest req) {
        contractService.voidContract(id, req.getReason());
        return Result.ok();
    }

    @Operation(summary = "合同详情")
    @GetMapping("/{id}")
    public Result<ContractVO> get(@PathVariable Long id) {
        return Result.ok(contractService.get(id));
    }

    @Operation(summary = "合同分页查询")
    @GetMapping
    public Result<PageResult<ContractVO>> page(ContractQueryRequest req) {
        return Result.ok(contractService.page(req));
    }

    @Operation(summary = "首页驾驶舱")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(contractService.dashboard());
    }

    @Data
    public static class VoidRequest {
        private String reason;
    }
}
