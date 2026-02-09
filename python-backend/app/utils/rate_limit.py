"""
分布式限流工具（基于 Redis）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from enum import Enum
from typing import Optional, Any

from fastapi import Request
from loguru import logger

from app.core.errors import BusinessException, ErrorCode

USER_LOGIN_STATE = "user_login_state"

KEY_PREFIX = "rate_limit:"
LIMITER_EXPIRE_SECONDS = 3600


class RateLimitType(str, Enum):
    API = "api"
    USER = "user"
    IP = "ip"


def _get_client_ip(request: Request) -> str:
    """
    获取客户端 IP
    """
    if request is None:
        return "unknown"
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded and forwarded.lower() != "unknown":
        ip = forwarded.split(",")[0].strip()
        if ip:
            return ip
    real_ip = request.headers.get("X-Real-IP")
    if real_ip and real_ip.lower() != "unknown":
        return real_ip
    if request.client:
        return request.client.host or "unknown"
    return "unknown"


def _build_rate_limit_key(
    request: Request,
    limit_type: RateLimitType,
    key_suffix: str = ""
) -> str:
    """
    构建限流 Redis Key
    """
    parts = [KEY_PREFIX]
    if key_suffix:
        parts.append(key_suffix)
        parts.append(":")
    if limit_type == RateLimitType.USER:
        session = getattr(request.state, "session", None) or {}
        user_info = session.get(USER_LOGIN_STATE, {}) if session else {}
        user_id = user_info.get("id") if user_info else None
        if user_id is not None:
            parts.append(f"user:{user_id}")
        else:
            parts.append(f"ip:{_get_client_ip(request)}")
    elif limit_type == RateLimitType.IP:
        parts.append(f"ip:{_get_client_ip(request)}")
    elif limit_type == RateLimitType.API:
        parts.append(f"api:{request.url.path}")
    else:
        raise BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型")
    return "".join(parts)


async def check_rate_limit(
    redis: Any,
    request: Request,
    limit_type: RateLimitType,
    rate: int,
    rate_interval: int,
    message: str = "请求过于频繁，请稍后再试",
    key_suffix: str = ""
) -> None:
    """
    检查限流，超过限制则抛出 BusinessException

    Args:
        redis: 异步 Redis 客户端
        request: FastAPI Request
        limit_type: 限流维度 USER / IP / API
        rate: 时间窗口内允许的请求数
        rate_interval: 时间窗口（秒）
        message: 超限时的提示消息
        key_suffix: 可选，Key 后缀

    Raises:
        BusinessException: 超过限流时抛出 TOO_MANY_REQUEST
    """
    if redis is None:
        return

    key = _build_rate_limit_key(request, limit_type, key_suffix)
    pipe = redis.pipeline()
    pipe.incr(key)
    pipe.ttl(key)
    results = await pipe.execute()
    count = results[0]
    ttl = results[1]

    if ttl < 0:
        await redis.expire(key, min(rate_interval, LIMITER_EXPIRE_SECONDS))

    if count > rate:
        logger.warning("限流触发: key={}", key)
        raise BusinessException(ErrorCode.TOO_MANY_REQUEST, message)
