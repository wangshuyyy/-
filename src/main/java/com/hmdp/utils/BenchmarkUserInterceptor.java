package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 仅在 hmdp.benchmark.enabled=true 时启用，便于JMeter生成大量独立用户。
 * 普通和docker profile默认关闭，不能作为生产鉴权方式。
 */
public class BenchmarkUserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String value = request.getHeader("X-Benchmark-User-Id");
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            UserDTO user = new UserDTO();
            user.setId(Long.valueOf(value));
            user.setNickName("benchmark-" + value);
            UserHolder.saveUser(user);
        } catch (NumberFormatException ignored) {
            response.setStatus(400);
            return false;
        }
        return true;
    }
}
