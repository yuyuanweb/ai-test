package com.yupi.template.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 请求响应日志 AOP
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Aspect
@Component
@Slf4j
public class LogInterceptor {

    /**
     * 执行拦截
     */
    @Around("execution(* com.yupi.template.controller.*.*(..))")
    public Object doInterceptor(ProceedingJoinPoint point) throws Throwable {
        Object result;
        try {
            // 生成请求唯一 id
            String requestId = UUID.randomUUID().toString();
            MDC.put("traceId", requestId);
            // 计时
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 获取请求路径
            RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
            HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();
            String ipAddress = getClientIP(httpServletRequest);
            String url = httpServletRequest.getRequestURI();
            // 获取请求参数
            Object[] args = point.getArgs();
            String reqParam = "[" + StringUtils.join(args, ", ") + "]";
            // 输出请求日志
            log.info("request start，id: {}, path: {}, ip: {}, ua: {}, params: {}", requestId, url, ipAddress, 
                    httpServletRequest.getHeader("User-Agent"), reqParam);
            // 执行原方法
            result = point.proceed();
            // 输出响应日志
            stopWatch.stop();
            long totalTimeMillis = stopWatch.getTotalTimeMillis();

            log.info("request end, id: {}, cost: {}ms", requestId, totalTimeMillis);
        } finally {
            MDC.remove("traceId");
        }
        return result;
    }

    /**
     * 获取客户端真实IP地址
     * 兼容代理、负载均衡等场景
     */
    private String getClientIP(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 对于通过多个代理的情况，第一个IP为客户端真实IP，多个IP按照','分割
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

}

