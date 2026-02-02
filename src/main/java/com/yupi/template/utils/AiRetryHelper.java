package com.yupi.template.utils;

import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * AI 非流式调用重试：仅对可重试异常（超时、5xx、429）重试，最多 3 次，固定间隔 2 秒。
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public final class AiRetryHelper {

    private static final int MAX_ATTEMPTS = 3;
    private static final long WAIT_SECONDS = 2;

    private static final com.github.rholder.retry.Retryer<Object> RETRYER = RetryerBuilder.newBuilder()
            .retryIfException(AiRetryHelper::isRetryable)
            .withStopStrategy(StopStrategies.stopAfterAttempt(MAX_ATTEMPTS))
            .withWaitStrategy(WaitStrategies.fixedWait(WAIT_SECONDS, TimeUnit.SECONDS))
            .build();

    private AiRetryHelper() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T runWithRetry(Callable<T> callable) throws Exception {
        return (T) RETRYER.call(() -> (Object) callable.call());
    }

    private static boolean isRetryable(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("429") || lower.contains("rate limit") || lower.contains("timeout")
                    || lower.contains("timed out") || lower.contains("connection reset")
                    || lower.contains("502") || lower.contains("503") || lower.contains("504")) {
                return true;
            }
        }
        return isRetryable(t.getCause());
    }
}
