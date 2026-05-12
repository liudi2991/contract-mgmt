package com.company.contract.service.contract;

import com.company.contract.domain.entity.Contract;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同状态机判定逻辑。详见技术规格 §5.4。
 */
public final class ContractStatusService {

    private ContractStatusService() {}

    /**
     * 根据当前金额与日期重新判定合同状态。
     * @return 计算后的状态（不持久化）
     */
    public static String evaluate(Contract c, LocalDate today) {
        if (Contract.STATUS_VOIDED.equals(c.getStatus())) {
            return Contract.STATUS_VOIDED;
        }
        BigDecimal remaining = c.getRemainingAmount() == null ? BigDecimal.ZERO : c.getRemainingAmount();
        boolean settled = remaining.compareTo(BigDecimal.ZERO) <= 0;
        boolean overExpire = today.isAfter(c.getExpireDate());

        if (settled && !today.isBefore(c.getExpireDate())) {
            return Contract.STATUS_COMPLETED;
        }
        if (overExpire && !settled) {
            return Contract.STATUS_EXPIRED;
        }
        return Contract.STATUS_EXECUTING;
    }
}
