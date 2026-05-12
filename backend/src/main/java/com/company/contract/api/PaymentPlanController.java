package com.company.contract.api;

import com.company.contract.audit.Audit;
import com.company.contract.common.Result;
import com.company.contract.domain.dto.PaymentPlanGenerateRequest;
import com.company.contract.domain.vo.PaymentPlanItemVO;
import com.company.contract.service.payment.PaymentPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "回款计划")
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/payment-plan")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;

    @GetMapping
    public Result<List<PaymentPlanItemVO>> list(@PathVariable Long contractId) {
        return Result.ok(paymentPlanService.listByContract(contractId));
    }

    @PostMapping
    @Audit(module = "PAYMENT_PLAN", action = "GENERATE", targetType = "Contract")
    public Result<List<PaymentPlanItemVO>> generate(@PathVariable Long contractId,
                                                    @Valid @RequestBody PaymentPlanGenerateRequest req) {
        return Result.ok(paymentPlanService.generate(contractId, req));
    }
}
