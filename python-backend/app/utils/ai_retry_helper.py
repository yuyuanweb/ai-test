"""
AI 非流式调用重试：仅对可重试异常（超时、5xx、429）重试，最多 3 次，固定间隔 2 秒
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import time
from typing import TypeVar, Callable, Awaitable

MAX_ATTEMPTS = 3
WAIT_SECONDS = 2

T = TypeVar("T")


def _is_retryable(exc: BaseException) -> bool:
    """
    判断异常是否可重试
    """
    if exc is None:
        return False
    msg = str(exc).lower()
    retryable_patterns = [
        "429", "rate limit", "timeout", "timed out",
        "connection reset", "502", "503", "504"
    ]
    for p in retryable_patterns:
        if p in msg:
            return True
    cause = getattr(exc, "__cause__", None)
    if cause:
        return _is_retryable(cause)
    return False


def run_with_retry(callable_fn: Callable[[], T]) -> T:
    """
    同步重试：仅对可重试异常重试

    Args:
        callable_fn: 无参可调用对象，返回 T

    Returns:
        调用结果

    Raises:
        最后一次的异常（若所有重试均失败）
    """
    last_exc: BaseException = None
    for attempt in range(MAX_ATTEMPTS):
        try:
            return callable_fn()
        except BaseException as e:
            last_exc = e
            if attempt < MAX_ATTEMPTS - 1 and _is_retryable(e):
                time.sleep(WAIT_SECONDS)
                continue
            raise
    if last_exc:
        raise last_exc
    raise RuntimeError("run_with_retry: unexpected state")


async def run_with_retry_async(coro_fn: Callable[[], Awaitable[T]]) -> T:
    """
    异步重试：仅对可重试异常重试

    Args:
        coro_fn: 无参可调用对象，返回协程

    Returns:
        调用结果

    Raises:
        最后一次的异常（若所有重试均失败）
    """
    last_exc: BaseException = None
    for attempt in range(MAX_ATTEMPTS):
        try:
            return await coro_fn()
        except BaseException as e:
            last_exc = e
            if attempt < MAX_ATTEMPTS - 1 and _is_retryable(e):
                await asyncio.sleep(WAIT_SECONDS)
                continue
            raise
    if last_exc:
        raise last_exc
    raise RuntimeError("run_with_retry_async: unexpected state")
