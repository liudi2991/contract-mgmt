package com.company.contract.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginLockService {

    private static final String FAIL_KEY = "login:fail:";
    private static final String LOCK_KEY = "login:lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.login.max-fail}")
    private int maxFail;

    @Value("${app.login.lock-minutes}")
    private int lockMinutes;

    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY + username));
    }

    public void onFail(String username) {
        Long count = redisTemplate.opsForValue().increment(FAIL_KEY + username);
        if (count != null && count == 1L) {
            redisTemplate.expire(FAIL_KEY + username, Duration.ofMinutes(lockMinutes));
        }
        if (count != null && count >= maxFail) {
            redisTemplate.opsForValue().set(LOCK_KEY + username, "1", Duration.ofMinutes(lockMinutes));
        }
    }

    public void onSuccess(String username) {
        redisTemplate.delete(FAIL_KEY + username);
        redisTemplate.delete(LOCK_KEY + username);
    }
}
