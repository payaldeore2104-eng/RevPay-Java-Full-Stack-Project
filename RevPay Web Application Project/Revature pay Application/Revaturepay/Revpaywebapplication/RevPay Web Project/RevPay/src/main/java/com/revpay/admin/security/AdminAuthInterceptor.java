package com.revpay.admin.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminAuthInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        String loginPath = request.getContextPath() + "/admin/login";
        if (uri != null && (uri.equals(loginPath) || uri.startsWith(loginPath + "/"))) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/admin/login");
            return false;
        }

        String loginId = (String) session.getAttribute("loggedInUser");
        if (loginId == null || loginId.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/admin/login");
            return false;
        }

        String role;
        try {
            role = jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE email = ? OR phone = ?",
                    String.class, loginId, loginId);
        } catch (Exception e) {
            role = null;
        }

        if (!"ROLE_ADMIN".equals(role)) {
            response.sendRedirect(request.getContextPath() + "/dashboard?error=access_denied");
            return false;
        }

        return true;
    }
}

