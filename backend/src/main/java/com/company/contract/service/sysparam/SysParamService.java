package com.company.contract.service.sysparam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.domain.entity.SysParam;
import com.company.contract.mapper.SysParamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysParamService {

    private final SysParamMapper sysParamMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "sys:param:";

    public String get(String key) {
        Object cached = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
        if (cached != null) return cached.toString();

        SysParam p = sysParamMapper.selectOne(new QueryWrapper<SysParam>().eq("param_key", key));
        if (p == null) return null;
        redisTemplate.opsForValue().set(CACHE_PREFIX + key, p.getParamValue(), Duration.ofMinutes(5));
        return p.getParamValue();
    }

    public String getOrDefault(String key, String defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<SysParam> listAll() {
        return sysParamMapper.selectList(new QueryWrapper<SysParam>().orderByAsc("param_key"));
    }

    @Transactional
    public void update(String key, String value) {
        SysParam p = sysParamMapper.selectOne(new QueryWrapper<SysParam>().eq("param_key", key));
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        p.setParamValue(value);
        sysParamMapper.updateById(p);
        redisTemplate.delete(CACHE_PREFIX + key);
    }
}
