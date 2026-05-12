package com.company.contract.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.domain.dto.PaymentPlanGenerateRequest;
import com.company.contract.domain.entity.Contract;
import com.company.contract.domain.entity.PaymentPlanItem;
import com.company.contract.domain.vo.PaymentPlanItemVO;
import com.company.contract.mapper.ContractMapper;
import com.company.contract.mapper.PaymentPlanItemMapper;
import com.company.contract.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentPlanService {

    private final ContractMapper contractMapper;
    private final PaymentPlanItemMapper planMapper;

    public List<PaymentPlanItemVO> listByContract(Long contractId) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        return planMapper.listByContractId(contractId).stream().map(this::toVO).toList();
    }

    @Transactional
    public List<PaymentPlanItemVO> generate(Long contractId, PaymentPlanGenerateRequest req) {
        Contract c = contractMapper.selectById(contractId);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        if (Contract.STATUS_VOIDED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "合同已作废");
        }

        // 校验没有已核销的期次（否则不允许重置）
        List<PaymentPlanItem> existing = planMapper.listByContractId(contractId);
        boolean anySettled = existing.stream()
                .anyMatch(it -> it.getReceivedAmount() != null
                        && it.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0);
        if (anySettled) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已有回款的合同不能重置计划，请逐期编辑");
        }

        if (!existing.isEmpty()) {
            planMapper.delete(new QueryWrapper<PaymentPlanItem>().eq("contract_id", contractId));
        }

        List<PaymentPlanItem> items = build(c, req);
        validateAmountConsistency(c.getAmount(), items);

        for (PaymentPlanItem item : items) {
            item.setContractId(contractId);
            item.setReceivedAmount(BigDecimal.ZERO);
            item.setStatus(PaymentPlanItem.STATUS_UNPAID);
            planMapper.insert(item);
        }
        return items.stream().map(this::toVO).toList();
    }

    private List<PaymentPlanItem> build(Contract c, PaymentPlanGenerateRequest req) {
        return switch (req.getMode()) {
            case MANUAL -> buildManual(c, req);
            case RATIO -> buildRatio(c, req);
            case UNIFORM -> buildUniform(c, req);
        };
    }

    private List<PaymentPlanItem> buildManual(Contract c, PaymentPlanGenerateRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手工模式需要填写期次列表");
        }
        List<PaymentPlanItem> result = new ArrayList<>();
        int idx = 1;
        for (PaymentPlanGenerateRequest.PlanItem it : req.getItems()) {
            if (it.getPlannedDate().isBefore(c.getSignDate())) {
                throw new BusinessException(ErrorCode.BUSINESS_INVALID, "计划日期不能早于签订日期");
            }
            PaymentPlanItem item = new PaymentPlanItem();
            item.setPeriodNo(idx++);
            item.setPlannedDate(it.getPlannedDate());
            item.setPlannedAmount(it.getPlannedAmount());
            item.setRemark(it.getRemark());
            result.add(item);
        }
        return result;
    }

    private List<PaymentPlanItem> buildRatio(Contract c, PaymentPlanGenerateRequest req) {
        if (req.getPeriods() == null || req.getRatios() == null
                || req.getRatios().size() != req.getPeriods()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "ratios 数量必须等于 periods");
        }
        BigDecimal sumRatio = req.getRatios().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumRatio.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "ratios 之和必须等于 100");
        }
        int intervalDays = req.getIntervalDays() == null ? 30 : req.getIntervalDays();
        LocalDate firstDate = req.getFirstDate() == null ? c.getSignDate().plusDays(intervalDays) : req.getFirstDate();

        List<PaymentPlanItem> result = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        int n = req.getPeriods();
        for (int i = 0; i < n; i++) {
            BigDecimal amount;
            if (i == n - 1) {
                amount = c.getAmount().subtract(accumulated);
            } else {
                amount = c.getAmount().multiply(req.getRatios().get(i))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                accumulated = accumulated.add(amount);
            }
            PaymentPlanItem item = new PaymentPlanItem();
            item.setPeriodNo(i + 1);
            item.setPlannedDate(firstDate.plusDays((long) intervalDays * i));
            item.setPlannedAmount(amount);
            result.add(item);
        }
        return result;
    }

    private List<PaymentPlanItem> buildUniform(Contract c, PaymentPlanGenerateRequest req) {
        if (req.getPeriods() == null || req.getPeriods() < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "periods 必须 >= 1");
        }
        int n = req.getPeriods();
        int intervalDays = req.getIntervalDays() == null ? 30 : req.getIntervalDays();
        LocalDate firstDate = req.getFirstDate() == null ? c.getSignDate().plusDays(intervalDays) : req.getFirstDate();
        BigDecimal each = c.getAmount().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        List<PaymentPlanItem> result = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal amount = (i == n - 1) ? c.getAmount().subtract(accumulated) : each;
            accumulated = accumulated.add(amount);

            PaymentPlanItem item = new PaymentPlanItem();
            item.setPeriodNo(i + 1);
            item.setPlannedDate(firstDate.plusDays((long) intervalDays * i));
            item.setPlannedAmount(amount);
            result.add(item);
        }
        return result;
    }

    private void validateAmountConsistency(BigDecimal contractAmount, List<PaymentPlanItem> items) {
        BigDecimal sum = items.stream().map(PaymentPlanItem::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(contractAmount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_INVALID,
                    "回款计划金额合计 " + sum + " 与合同金额 " + contractAmount + " 不一致");
        }
    }

    public PaymentPlanItemVO toVO(PaymentPlanItem i) {
        return PaymentPlanItemVO.builder()
                .id(i.getId())
                .contractId(i.getContractId())
                .periodNo(i.getPeriodNo())
                .plannedDate(i.getPlannedDate())
                .plannedAmount(i.getPlannedAmount())
                .receivedAmount(i.getReceivedAmount())
                .status(i.getStatus())
                .remark(i.getRemark())
                .build();
    }
}
