package com.hgx.hgxaiagent.user.interceptor;

import com.hgx.hgxaiagent.user.constant.UserConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录态拦截器。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Object loginUser = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (loginUser != null) {
            return true;
        }

        writeUnauthorizedResponse(response);
        return false;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"code":401,"message":"请先登录"}
                """);
    }
}
