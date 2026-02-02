package com.yupi.template.ratelimit;

import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 限流切面，基于 Redisson RRateLimiter 实现分布式限流
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate_limit:";
    private static final Duration LIMITER_EXPIRE = Duration.ofHours(1);

    @Resource
    private org.redisson.api.RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        String key = buildKey(point, rateLimit);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.expire(LIMITER_EXPIRE);
        limiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire(1)) {
            log.warn("限流触发: key={}", key);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    private String buildKey(JoinPoint point, RateLimit rateLimit) {
        var sb = new StringBuilder(KEY_PREFIX);
        if (rateLimit.key() != null && !rateLimit.key().isBlank()) {
            sb.append(rateLimit.key()).append(":");
        }
        switch (rateLimit.limitType()) {
            case API -> {
                MethodSignature sig = (MethodSignature) point.getSignature();
                Method m = sig.getMethod();
                sb.append("api:").append(m.getDeclaringClass().getSimpleName()).append(".").append(m.getName());
            }
            case USER -> {
                try {
                    HttpServletRequest req = currentRequest();
                    if (req != null) {
                        User u = userService.getLoginUser(req);
                        sb.append("user:").append(u.getId());
                    } else {
                        sb.append("ip:").append(getClientIp(null));
                    }
                } catch (BusinessException e) {
                    sb.append("ip:").append(getClientIp(currentRequest()));
                }
            }
            case IP -> sb.append("ip:").append(getClientIp(currentRequest()));
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型");
        }
        return sb.toString();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        return sra.getRequest();
    }

    private String getClientIp(HttpServletRequest req) {
        if (req == null) {
            return "unknown";
        }
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
