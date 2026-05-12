package com.company.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.contract.domain.entity.Contract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ContractMapper extends BaseMapper<Contract> {

    @Select("SELECT * FROM contract WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Contract selectForUpdate(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(contract_no, '-', -1) AS UNSIGNED)), 0) " +
            "FROM contract WHERE YEAR(sign_date) = #{year} AND deleted = 0")
    Long maxSerialOfYear(@Param("year") int year);
}
