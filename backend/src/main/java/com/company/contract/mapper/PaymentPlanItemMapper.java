package com.company.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.contract.domain.entity.PaymentPlanItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PaymentPlanItemMapper extends BaseMapper<PaymentPlanItem> {

    @Select("SELECT * FROM payment_plan_item WHERE contract_id = #{contractId} AND deleted = 0 ORDER BY period_no ASC")
    List<PaymentPlanItem> listByContractId(@Param("contractId") Long contractId);

    @Select("SELECT * FROM payment_plan_item WHERE contract_id = #{contractId} AND deleted = 0 " +
            "AND status <> 'SETTLED' ORDER BY planned_date ASC, period_no ASC FOR UPDATE")
    List<PaymentPlanItem> listUnsettledForUpdate(@Param("contractId") Long contractId);

    @Select("SELECT * FROM payment_plan_item WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    PaymentPlanItem selectForUpdate(@Param("id") Long id);
}
