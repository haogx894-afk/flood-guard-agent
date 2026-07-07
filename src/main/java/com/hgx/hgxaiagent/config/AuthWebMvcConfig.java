package com.hgx.hgxaiagent.config;

import com.hgx.hgxaiagent.user.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 登录鉴权配置。
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public AuthWebMvcConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login",
                        "/user/logout",
                        "/health",
                        "/error",
                        "/favicon.ico",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                );
    }
}
