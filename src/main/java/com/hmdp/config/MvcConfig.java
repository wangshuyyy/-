package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.BenchmarkUserInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${hmdp.benchmark.enabled:false}")
    private boolean benchmarkEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (benchmarkEnabled) {
            registry.addInterceptor(new BenchmarkUserInterceptor()).addPathPatterns("/**").order(0);
        }
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(1);
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(2);
    }
}
