package com.company.contract.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import com.company.contract.common.PageResult;
import com.company.contract.domain.dto.UserCreateRequest;
import com.company.contract.domain.dto.UserUpdateRequest;
import com.company.contract.domain.entity.SysUser;
import com.company.contract.domain.vo.UserVO;
import com.company.contract.mapper.SysUserMapper;
import com.company.contract.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    public PageResult<UserVO> page(int page, int size, String keyword) {
        Page<SysUser> p = new Page<>(page, size);
        QueryWrapper<SysUser> q = new QueryWrapper<SysUser>().eq("deleted", 0);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("username", keyword).or().like("name", keyword));
        }
        q.orderByDesc("id");
        sysUserMapper.selectPage(p, q);
        return PageResult.of(p, AuthService::toVO);
    }

    public List<UserVO> listAll() {
        return sysUserMapper.selectList(new QueryWrapper<SysUser>()
                .eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("name"))
                .stream().map(AuthService::toVO).toList();
    }

    @Transactional
    public Map<String, String> create(UserCreateRequest req) {
        Long existing = sysUserMapper.selectCount(new QueryWrapper<SysUser>()
                .eq("username", req.getUsername())
                .eq("deleted", 0));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        String rawPwd = randomPassword();

        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setRole(req.getRole());
        u.setStatus(1);
        u.setMustChangePwd(1);
        u.setPasswordHash(passwordEncoder.encode(rawPwd));
        sysUserMapper.insert(u);

        return Map.of("username", u.getUsername(), "initialPassword", rawPwd);
    }

    @Transactional
    public void update(Long id, UserUpdateRequest req) {
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setRole(req.getRole());
        sysUserMapper.updateById(u);
    }

    @Transactional
    public void disable(Long id) {
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        u.setStatus(0);
        sysUserMapper.updateById(u);
    }

    @Transactional
    public Map<String, String> resetPassword(Long id) {
        SysUser u = sysUserMapper.selectById(id);
        if (u == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        String rawPwd = randomPassword();
        u.setPasswordHash(passwordEncoder.encode(rawPwd));
        u.setMustChangePwd(1);
        sysUserMapper.updateById(u);
        return Map.of("username", u.getUsername(), "newPassword", rawPwd);
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(PWD_CHARS.charAt(RANDOM.nextInt(PWD_CHARS.length())));
        }
        return sb.toString();
    }
}
