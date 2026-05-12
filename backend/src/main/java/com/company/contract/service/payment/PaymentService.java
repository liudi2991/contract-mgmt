package com.company.contract.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.common.PageResult;
import com.company.contract.domain.dto.PaymentCreateRequest;
import com.company.contract.domain.entity.Contract;
import com.company.contract.domain.entity.Payment;
import com.company.contract.domain.entity.PaymentPlanItem;
import com.company.contract.domain.entity.SysUser;
import com.company.contract.domain.vo.PaymentVO;
import com.company.contract.mapper.ContractMapper;
import com.company.contract.mapper.PaymentMapper;
import com.company.contract.mapper.PaymentPlanItemMapper;
import com.company.contract.mapper.SysUserMapper;
import com.company.contract.security.SecurityHelper;
import com.company.contract.service.contract.ContractStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回款核销算法（系统核心）。
 *
 * 设计要点（详见技术规格 §5.2）：
 * 1. @Transactional 包裹，事务内完成 payment 落库 + plan_item 核销 + contract 冗余字段更新
 * 2. 通过 SELECT ... FOR UPDATE 锁定 contract 行，避免并发回款时余额计算错误
 * 3. 指定 planItemId → 直接核销该期；未指定 → 按 planned_date asc 顺序抵扣
 * 4. 超额回款挂在合同上，不分摊到期次（合同冗余字段会正确反映）
 * 5. 删除/编辑回款时反向回退到原期次，重算期次状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentPlanItemMapper planMapper;
    private final ContractMapper contractMapper;
    private final SysUserMapper sysUserMapper;
    private final PaymentPlanService paymentPlanService;

    @Transactional
    public PaymentVO create(PaymentCreateRequest req) {
        Contract c = contractMapper.selectForUpdate(req.getContractId());
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        if (Contract.STATUS_VOIDED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "合同已作废，不可登记回款");
        }

        Payment payment = new Payment();
        payment.setContractId(c.getId());
        payment.setPlanItemId(req.getPlanItemId());
        payment.setReceivedDate(req.getReceivedDate());
        payment.setAmount(req.getAmount());
        payment.setPayer(req.getPayer() == null || req.getPayer().isBlank() ? c.getCustomerName() : req.getPayer());
        payment.setBankSerial(req.getBankSerial());
        payment.setRemark(req.getRemark());
        paymentMapper.insert(payment);

        List<PaymentVO.SettlementVO> settlements = settle(c.getId(), req.getPlanItemId(), req.getAmount());

        updateContractAfterPayment(c, req.getAmount());

        return toVO(payment, settlements, c);
    }

    @Transactional
    public void delete(Long paymentId) {
        Payment p = paymentMapper.selectById(paymentId);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        Contract c = contractMapper.selectForUpdate(p.getContractId());
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());

        unsettle(c.getId(), p.getPlanItemId(), p.getAmount());

        paymentMapper.deleteById(paymentId);

        c.setPaidAmount(safe(c.getPaidAmount()).subtract(p.getAmount()).max(BigDecimal.ZERO));
        c.setRemainingAmount(c.getAmount().subtract(c.getPaidAmount()));
        c.setStatus(ContractStatusService.evaluate(c, LocalDate.now()));
        contractMapper.updateById(c);
    }

    /**
     * 核销主算法。
     */
    private List<PaymentVO.SettlementVO> settle(Long contractId, Long planItemId, BigDecimal amount) {
        List<PaymentVO.SettlementVO> result = new ArrayList<>();

        if (planItemId != null) {
            PaymentPlanItem item = planMapper.selectForUpdate(planItemId);
            if (item == null || !item.getContractId().equals(contractId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "回款期次与合同不匹配");
            }
            applyToItem(item, amount);
            result.add(toSettlement(item, amount));
            return result;
        }

        List<PaymentPlanItem> unsettled = planMapper.listUnsettledForUpdate(contractId);
        BigDecimal remaining = amount;
        for (PaymentPlanItem item : unsettled) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal gap = item.getPlannedAmount().subtract(safe(item.getReceivedAmount()));
            if (gap.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal applied = remaining.min(gap);
            applyToItem(item, applied);
            result.add(toSettlement(item, applied));
            remaining = remaining.subtract(applied);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Contract {} 超额回款 {}，未分摊到期次", contractId, remaining);
        }
        return result;
    }

    /**
     * 删除回款时反向回退核销。
     * 如果该回款指定了期次：直接从该期次扣减；
     * 否则按 period_no DESC（即后入先回退）从已核销的期次反向扣减，直到金额扣完。
     */
    private void unsettle(Long contractId, Long planItemId, BigDecimal amount) {
        if (planItemId != null) {
            PaymentPlanItem item = planMapper.selectForUpdate(planItemId);
            if (item != null) {
                BigDecimal newReceived = safe(item.getReceivedAmount()).subtract(amount).max(BigDecimal.ZERO);
                item.setReceivedAmount(newReceived);
                item.setStatus(decideItemStatus(newReceived, item.getPlannedAmount()));
                planMapper.updateById(item);
            }
            return;
        }
        List<PaymentPlanItem> all = planMapper.listByContractId(contractId);
        all.sort((a, b) -> b.getPeriodNo() - a.getPeriodNo());
        BigDecimal remaining = amount;
        for (PaymentPlanItem item : all) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal received = safe(item.getReceivedAmount());
            if (received.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal back = received.min(remaining);
            BigDecimal newReceived = received.subtract(back);
            item.setReceivedAmount(newReceived);
            item.setStatus(decideItemStatus(newReceived, item.getPlannedAmount()));
            planMapper.updateById(item);
            remaining = remaining.subtract(back);
        }
    }

    private void applyToItem(PaymentPlanItem item, BigDecimal delta) {
        BigDecimal newReceived = safe(item.getReceivedAmount()).add(delta);
        item.setReceivedAmount(newReceived);
        item.setStatus(decideItemStatus(newReceived, item.getPlannedAmount()));
        planMapper.updateById(item);
    }

    private String decideItemStatus(BigDecimal received, BigDecimal planned) {
        if (received.compareTo(planned) >= 0) return PaymentPlanItem.STATUS_SETTLED;
        if (received.compareTo(BigDecimal.ZERO) > 0) return PaymentPlanItem.STATUS_PARTIAL;
        return PaymentPlanItem.STATUS_UNPAID;
    }

    private void updateContractAfterPayment(Contract c, BigDecimal delta) {
        c.setPaidAmount(safe(c.getPaidAmount()).add(delta));
        c.setRemainingAmount(c.getAmount().subtract(c.getPaidAmount()));
        c.setStatus(ContractStatusService.evaluate(c, LocalDate.now()));
        contractMapper.updateById(c);
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private PaymentVO.SettlementVO toSettlement(PaymentPlanItem item, BigDecimal applied) {
        return PaymentVO.SettlementVO.builder()
                .planItemId(item.getId())
                .periodNo(item.getPeriodNo())
                .settledAmount(applied)
                .planItemStatus(item.getStatus())
                .build();
    }

    /* =========================== Query =========================== */

    public PaymentVO get(Long id) {
        Payment p = paymentMapper.selectById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        Contract c = contractMapper.selectById(p.getContractId());
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        return toVO(p, null, c);
    }

    public PageResult<PaymentVO> page(int page, int size, Long contractId, LocalDate dateFrom, LocalDate dateTo) {
        Page<Payment> p = new Page<>(page, size);
        QueryWrapper<Payment> q = new QueryWrapper<Payment>().eq("deleted", 0);
        if (!SecurityHelper.isAdmin()) {
            List<Long> ownedContracts = contractMapper.selectList(
                    new QueryWrapper<Contract>().eq("owner_id", SecurityHelper.currentUserId()).eq("deleted", 0)
            ).stream().map(Contract::getId).toList();
            if (ownedContracts.isEmpty()) {
                return new PageResult<>(0, List.of());
            }
            q.in("contract_id", ownedContracts);
        }
        if (contractId != null) q.eq("contract_id", contractId);
        if (dateFrom != null) q.ge("received_date", dateFrom);
        if (dateTo != null) q.le("received_date", dateTo);
        q.orderByDesc("received_date").orderByDesc("id");

        paymentMapper.selectPage(p, q);
        Map<Long, Contract> contractMap = new HashMap<>();
        Map<Long, SysUser> userMap = new HashMap<>();
        for (Payment payment : p.getRecords()) {
            contractMap.computeIfAbsent(payment.getContractId(), contractMapper::selectById);
            if (payment.getCreatedBy() != null) {
                userMap.computeIfAbsent(payment.getCreatedBy(), sysUserMapper::selectById);
            }
        }
        List<PaymentVO> records = p.getRecords().stream()
                .map(pay -> {
                    Contract c = contractMap.get(pay.getContractId());
                    SysUser u = userMap.get(pay.getCreatedBy());
                    return PaymentVO.builder()
                            .id(pay.getId())
                            .contractId(pay.getContractId())
                            .contractNo(c == null ? null : c.getContractNo())
                            .customerName(c == null ? null : c.getCustomerName())
                            .planItemId(pay.getPlanItemId())
                            .receivedDate(pay.getReceivedDate())
                            .amount(pay.getAmount())
                            .payer(pay.getPayer())
                            .bankSerial(pay.getBankSerial())
                            .remark(pay.getRemark())
                            .createdBy(pay.getCreatedBy())
                            .createdByName(u == null ? null : u.getName())
                            .createdAt(pay.getCreatedAt())
                            .build();
                }).toList();
        return new PageResult<>(p.getTotal(), records);
    }

    private PaymentVO toVO(Payment p, List<PaymentVO.SettlementVO> settlements, Contract c) {
        return PaymentVO.builder()
                .id(p.getId())
                .contractId(p.getContractId())
                .contractNo(c == null ? null : c.getContractNo())
                .customerName(c == null ? null : c.getCustomerName())
                .planItemId(p.getPlanItemId())
                .receivedDate(p.getReceivedDate())
                .amount(p.getAmount())
                .payer(p.getPayer())
                .bankSerial(p.getBankSerial())
                .remark(p.getRemark())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .settlements(settlements)
                .contractPaidAmount(c == null ? null : c.getPaidAmount())
                .contractRemainingAmount(c == null ? null : c.getRemainingAmount())
                .build();
    }
}
