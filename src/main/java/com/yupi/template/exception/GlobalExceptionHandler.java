package com.yupi.template.exception;

import cn.hutool.json.JSONUtil;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

/**
 * 全局异常处理器。对 SSE 请求返回 business-error 事件，便于前端区分限流等业务错误。
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String EVENT_BUSINESS_ERROR = "business-error";
    private static final String EVENT_DONE = "done";

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        HttpServletRequest request = attrs.getRequest();
        HttpServletResponse response = attrs.getResponse();
        if (request == null || response == null) {
            return false;
        }
        if (!isSseRequest(request)) {
            return false;
        }
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            Map<String, Object> payload = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage != null ? errorMessage : "未知错误"
            );
            String json = JSONUtil.toJsonStr(payload);
            response.getWriter().write("event: " + EVENT_BUSINESS_ERROR + "\ndata: " + json + "\n\n");
            response.getWriter().flush();
            response.getWriter().write("event: " + EVENT_DONE + "\ndata: {}\n\n");
            response.getWriter().flush();
            return true;
        } catch (IOException ex) {
            log.error("写入 SSE 错误响应失败", ex);
            return true;
        }
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/stream");
    }
}
