package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.PageResult;
import com.company.contract.common.Result;
import com.company.contract.domain.dto.PaymentCreateRequest;
import com.company.contract.domain.vo.PaymentVO;
import com.company.contract.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "实际回款")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "登记回款")
    @PostMapping
    @Audit(module = "PAYMENT", action = "CREATE", targetType = "Payment")
    public Result<PaymentVO> create(@Valid @RequestBody PaymentCreateRequest req) {
        return Result.ok(paymentService.create(req));
    }

    @Operation(summary = "回款详情")
    @GetMapping("/{id}")
    public Result<PaymentVO> get(@PathVariable Long id) {
        return Result.ok(paymentService.get(id));
    }

    @Operation(summary = "回款分页")
    @GetMapping
    public Result<PageResult<PaymentVO>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) Long contractId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(paymentService.page(page, size, contractId, dateFrom, dateTo));
    }

    @Operation(summary = "删除回款（反向核销）")
    @DeleteMapping("/{id}")
    @Audit(module = "PAYMENT", action = "DELETE", targetType = "Payment")
    public Result<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return Result.ok();
    }
}
