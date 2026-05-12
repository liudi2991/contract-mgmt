package com.company.contract.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.domain.dto.ChangePasswordRequest;
import com.company.contract.domain.dto.LoginRequest;
import com.company.contract.domain.entity.SysUser;
import com.company.contract.domain.vo.LoginResponse;
import com.company.contract.domain.vo.UserVO;
import com.company.contract.mapper.SysUserMapper;
import com.company.contract.security.JwtAuthenticationFilter;
import com.company.contract.security.JwtUtil;
import com.company.contract.security.LoginLockService;
import com.company.contract.security.SecurityHelper;
import com.company.contract.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginLockService loginLockService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        if (loginLockService.isLocked(req.getUsername())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        SysUser user = sysUserMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", req.getUsername())
                .eq("deleted", 0));
        if (user == null) {
            loginLockService.onFail(req.getUsername());
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            loginLockService.onFail(req.getUsername());
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }

        loginLockService.onSuccess(req.getUsername());

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getUsername(), user.getName(),
                user.getRole(), user.getMustChangePwd() == 1);
        JwtUtil.TokenResult t = jwtUtil.generate(principal);

        return LoginResponse.builder()
                .token(t.getToken())
                .expiresIn(t.getExpiresIn())
                .user(toVO(user))
                .build();
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        try {
            Claims claims = jwtUtil.parse(token);
            String jti = claims.getId();
            long ttl = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
            if (jti != null && ttl > 0) {
                redisTemplate.opsForValue().set(
                        JwtAuthenticationFilter.BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttl));
            }
        } catch (Exception ignore) {}
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        Long uid = SecurityHelper.currentUserId();
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePwd(0);
        sysUserMapper.updateById(user);
    }

    public UserVO currentUser() {
        Long uid = SecurityHelper.currentUserId();
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return toVO(user);
    }

    public static UserVO toVO(SysUser u) {
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole())
                .status(u.getStatus())
                .mustChangePwd(u.getMustChangePwd() != null && u.getMustChangePwd() == 1)
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
