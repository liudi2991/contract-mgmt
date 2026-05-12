package com.company.contract.service.contract;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.common.PageResult;
import com.company.contract.domain.dto.ContractCreateRequest;
import com.company.contract.domain.dto.ContractQueryRequest;
import com.company.contract.domain.dto.ContractUpdateRequest;
import com.company.contract.domain.entity.Contract;
import com.company.contract.domain.entity.SysUser;
import com.company.contract.domain.vo.ContractVO;
import com.company.contract.mapper.ContractMapper;
import com.company.contract.mapper.PaymentMapper;
import com.company.contract.mapper.SysUserMapper;
import com.company.contract.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractMapper contractMapper;
    private final PaymentMapper paymentMapper;
    private final SysUserMapper sysUserMapper;
    private final ContractNoGenerator contractNoGenerator;

    @Transactional
    public ContractVO create(ContractCreateRequest req) {
        validateDates(req.getSignDate(), req.getEffectiveDate(), req.getExpireDate());

        Long currentUserId = SecurityHelper.currentUserId();

        // 销售员只能为自己建合同
        if (!SecurityHelper.isAdmin() && !currentUserId.equals(req.getOwnerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为自己创建合同");
        }

        Contract c = new Contract();
        c.setContractNo(contractNoGenerator.next());
        c.setName(req.getName());
        c.setCustomerName(req.getCustomerName());
        c.setAmount(req.getAmount());
        c.setCurrency(req.getCurrency() == null ? "CNY" : req.getCurrency());
        c.setSignDate(req.getSignDate());
        c.setEffectiveDate(req.getEffectiveDate());
        c.setExpireDate(req.getExpireDate());
        c.setOwnerId(req.getOwnerId());
        c.setStatus(Contract.STATUS_EXECUTING);
        c.setPaidAmount(BigDecimal.ZERO);
        c.setRemainingAmount(req.getAmount());
        c.setRemark(req.getRemark());
        contractMapper.insert(c);

        return toVO(c);
    }

    @Transactional
    public ContractVO update(Long id, ContractUpdateRequest req) {
        Contract c = contractMapper.selectById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);

        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        if (Contract.STATUS_VOIDED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "合同已作废，不可编辑");
        }
        validateDates(req.getSignDate(), req.getEffectiveDate(), req.getExpireDate());

        boolean hasPayments = paymentMapper.selectCount(new QueryWrapper<com.company.contract.domain.entity.Payment>()
                .eq("contract_id", id)
                .eq("deleted", 0)) > 0;

        if (hasPayments) {
            // 已有回款时仅允许修改备注、附件、到期日期、负责人
            if (req.getAmount().compareTo(c.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "已存在回款，不允许修改合同金额");
            }
            if (!req.getSignDate().equals(c.getSignDate())) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "已存在回款，不允许修改签订日期");
            }
        } else {
            c.setAmount(req.getAmount());
            c.setSignDate(req.getSignDate());
            c.setRemainingAmount(req.getAmount());
        }
        c.setName(req.getName());
        c.setCustomerName(req.getCustomerName());
        c.setEffectiveDate(req.getEffectiveDate());
        c.setExpireDate(req.getExpireDate());
        c.setOwnerId(req.getOwnerId());
        c.setRemark(req.getRemark());

        c.setStatus(ContractStatusService.evaluate(c, LocalDate.now()));
        contractMapper.updateById(c);

        return toVO(c);
    }

    @Transactional
    public void voidContract(Long id, String reason) {
        Contract c = contractMapper.selectById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        if (Contract.STATUS_VOIDED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "合同已作废");
        }
        c.setStatus(Contract.STATUS_VOIDED);
        c.setVoidReason(reason);
        contractMapper.updateById(c);
    }

    public ContractVO get(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        SecurityHelper.assertOwnerOrAdmin(c.getOwnerId());
        return toVO(c);
    }

    public PageResult<ContractVO> page(ContractQueryRequest req) {
        Page<Contract> p = new Page<>(req.safePage(), req.safeSize());
        QueryWrapper<Contract> q = new QueryWrapper<Contract>().eq("deleted", 0);

        if (!SecurityHelper.isAdmin()) {
            q.eq("owner_id", SecurityHelper.currentUserId());
        } else if (req.getOwnerId() != null) {
            q.eq("owner_id", req.getOwnerId());
        }

        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String k = req.getKeyword();
            q.and(w -> w.like("contract_no", k).or().like("name", k));
        }
        if (req.getCustomerName() != null && !req.getCustomerName().isBlank()) {
            q.like("customer_name", req.getCustomerName());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            q.eq("status", req.getStatus());
        }
        if (req.getSignDateFrom() != null) q.ge("sign_date", req.getSignDateFrom());
        if (req.getSignDateTo() != null) q.le("sign_date", req.getSignDateTo());
        if (req.getExpireDateFrom() != null) q.ge("expire_date", req.getExpireDateFrom());
        if (req.getExpireDateTo() != null) q.le("expire_date", req.getExpireDateTo());
        q.orderByDesc("sign_date").orderByDesc("id");

        contractMapper.selectPage(p, q);
        return PageResult.of(p, this::toVO);
    }

    public ContractVO toVO(Contract c) {
        return ContractVO.builder()
                .id(c.getId())
                .contractNo(c.getContractNo())
                .name(c.getName())
                .customerName(c.getCustomerName())
                .amount(c.getAmount())
                .currency(c.getCurrency())
                .signDate(c.getSignDate())
                .effectiveDate(c.getEffectiveDate())
                .expireDate(c.getExpireDate())
                .ownerId(c.getOwnerId())
                .ownerName(loadOwnerName(c.getOwnerId()))
                .status(c.getStatus())
                .voidReason(c.getVoidReason())
                .paidAmount(c.getPaidAmount())
                .remainingAmount(c.getRemainingAmount())
                .remark(c.getRemark())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private final Map<Long, String> ownerNameCache = new HashMap<>();
    private String loadOwnerName(Long ownerId) {
        if (ownerId == null) return null;
        return ownerNameCache.computeIfAbsent(ownerId, id -> {
            SysUser u = sysUserMapper.selectById(id);
            return u == null ? null : u.getName();
        });
    }

    private void validateDates(LocalDate sign, LocalDate effective, LocalDate expire) {
        if (effective.isBefore(sign)) {
            throw new BusinessException(ErrorCode.BUSINESS_INVALID, "生效日期不能早于签订日期");
        }
        if (expire.isBefore(effective)) {
            throw new BusinessException(ErrorCode.BUSINESS_INVALID, "到期日期不能早于生效日期");
        }
    }

    public Map<String, Object> dashboard() {
        boolean admin = SecurityHelper.isAdmin();
        QueryWrapper<Contract> baseQuery = new QueryWrapper<Contract>().eq("deleted", 0);
        if (!admin) baseQuery.eq("owner_id", SecurityHelper.currentUserId());

        List<Contract> all = contractMapper.selectList(baseQuery);
        BigDecimal totalAmount = all.stream().map(Contract::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = all.stream().map(Contract::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalAmount.subtract(paidAmount);

        long executingCount = all.stream().filter(c -> Contract.STATUS_EXECUTING.equals(c.getStatus())).count();
        long expiredCount = all.stream().filter(c -> Contract.STATUS_EXPIRED.equals(c.getStatus())).count();
        long completedCount = all.stream().filter(c -> Contract.STATUS_COMPLETED.equals(c.getStatus())).count();

        Map<String, Object> r = new HashMap<>();
        r.put("totalCount", all.size());
        r.put("totalAmount", totalAmount);
        r.put("paidAmount", paidAmount);
        r.put("remainingAmount", remaining);
        r.put("executingCount", executingCount);
        r.put("expiredCount", expiredCount);
        r.put("completedCount", completedCount);
        return r;
    }
}
