package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.limiter.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RateLimitException.class)
    public Result handleRateLimitException(RateLimitException e) {
        return Result.fail(e.getMessage());
    }

    // 全局异常处理捕捉，记录通用日志，返回给前端通用响应 Result.fail("服务器异常")
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常:" + e.getMessage());
    }
}
