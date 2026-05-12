package com.company.contract.security;

import com.company.contract.common.BusinessException;
import com.company.contract.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityHelper {

    private SecurityHelper() {}

    public static UserPrincipal currentUser() {
        UserPrincipal u = currentUserOrNull();
        if (u == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return u;
    }

    public static UserPrincipal currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) return up;
        return null;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    public static Long currentUserIdOrNull() {
        UserPrincipal u = currentUserOrNull();
        return u == null ? null : u.getId();
    }

    public static boolean isAdmin() {
        UserPrincipal u = currentUserOrNull();
        return u != null && u.isAdmin();
    }

    public static void assertAdmin() {
        if (!isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    /**
     * 数据级越权校验：管理员任意；销售员只能操作自己负责的资源。
     */
    public static void assertOwnerOrAdmin(Long ownerId) {
        UserPrincipal u = currentUser();
        if (u.isAdmin()) return;
        if (ownerId == null || !ownerId.equals(u.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
