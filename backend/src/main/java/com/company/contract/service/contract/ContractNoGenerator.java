package com.company.contract.service.contract;

import com.company.contract.mapper.ContractMapper;
import com.company.contract.service.sysparam.SysParamService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 合同编号生成：Redis INCR + 数据库唯一索引兜底。
 *
 * 启动时校对：从 DB 取当前年份 MAX(流水)，若 Redis 中小于此值则重置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractNoGenerator {

    private static final String SEQ_PREFIX = "contract:no:seq:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ContractMapper contractMapper;
    private final SysParamService sysParamService;

    @PostConstruct
    public void calibrate() {
        try {
            int year = LocalDate.now().getYear();
            String key = SEQ_PREFIX + year;
            Long max = contractMapper.maxSerialOfYear(year);
            Object current = redisTemplate.opsForValue().get(key);
            long currentVal = current == null ? 0L : Long.parseLong(current.toString());
            if (max != null && max > currentVal) {
                redisTemplate.opsForValue().set(key, max);
                log.info("Calibrated contract.no seq for {} to {}", year, max);
            }
        } catch (Exception e) {
            log.warn("calibrate contract.no seq fail: {}", e.getMessage());
        }
    }

    public String next() {
        int year = LocalDate.now().getYear();
        String prefix = sysParamService.getOrDefault("contract.no.prefix", "HT");
        int length = sysParamService.getInt("contract.no.serial_length", 4);
        String key = SEQ_PREFIX + year;

        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) seq = 1L;
        return String.format("%s-%d-%0" + length + "d", prefix, year, seq);
    }
}
