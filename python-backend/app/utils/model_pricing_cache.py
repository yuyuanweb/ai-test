"""
模型价格 Redis 缓存工具
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
from decimal import Decimal
from typing import Optional, Tuple, Any

from app.core.constants import CacheConstant
from loguru import logger


def _cache_key(model_name: str) -> str:
    return f"{CacheConstant.MODEL_PRICING_KEY_PREFIX}{model_name}"


def _serialize_pricing(input_price: Optional[Decimal], output_price: Optional[Decimal]) -> str:
    in_str = str(input_price) if input_price is not None else None
    out_str = str(output_price) if output_price is not None else None
    return json.dumps({"input_price": in_str, "output_price": out_str})


def _deserialize_pricing(data: str) -> Tuple[Optional[Decimal], Optional[Decimal]]:
    try:
        obj = json.loads(data)
        in_val = Decimal(obj["input_price"]) if obj.get("input_price") else None
        out_val = Decimal(obj["output_price"]) if obj.get("output_price") else None
        return in_val, out_val
    except (json.JSONDecodeError, KeyError, Exception) as e:
        logger.warning(f"反序列化模型价格缓存失败: {e}")
        return None, None


async def get_model_pricing_cached_async(
    redis: Any,
    model_name: str,
    fetch_pricing_fn
) -> Tuple[Optional[Decimal], Optional[Decimal]]:
    """
    获取模型价格（异步，带 Redis 缓存）

    Args:
        redis: 异步 Redis 客户端
        model_name: 模型名称
        fetch_pricing_fn: 异步函数 () -> (input_price, output_price)，缓存未命中时调用

    Returns:
        (input_price, output_price)，任一为 None 时表示无有效价格
    """
    if not model_name or not model_name.strip():
        return None, None

    key = _cache_key(model_name)
    ttl_seconds = CacheConstant.MODEL_PRICING_TTL_HOURS * 3600

    try:
        cached = await redis.get(key)
        if cached:
            return _deserialize_pricing(cached)
    except Exception as e:
        logger.warning(f"读取模型价格缓存失败: model_name={model_name}, error={e}")

    input_price, output_price = await fetch_pricing_fn()

    try:
        await redis.setex(key, ttl_seconds, _serialize_pricing(input_price, output_price))
    except Exception as e:
        logger.warning(f"写入模型价格缓存失败: model_name={model_name}, error={e}")

    return input_price, output_price


def get_model_pricing_cached_sync(
    redis: Any,
    model_name: str,
    fetch_pricing_fn
) -> Tuple[Optional[Decimal], Optional[Decimal]]:
    """
    获取模型价格（同步，带 Redis 缓存）

    Args:
        redis: 同步 Redis 客户端
        model_name: 模型名称
        fetch_pricing_fn: 同步函数 () -> (input_price, output_price)，缓存未命中时调用

    Returns:
        (input_price, output_price)，任一为 None 时表示无有效价格
    """
    if not model_name or not model_name.strip():
        return None, None

    key = _cache_key(model_name)
    ttl_seconds = CacheConstant.MODEL_PRICING_TTL_HOURS * 3600

    try:
        cached = redis.get(key)
        if cached:
            return _deserialize_pricing(cached)
    except Exception as e:
        logger.warning(f"读取模型价格缓存失败: model_name={model_name}, error={e}")

    input_price, output_price = fetch_pricing_fn()

    try:
        redis.setex(key, ttl_seconds, _serialize_pricing(input_price, output_price))
    except Exception as e:
        logger.warning(f"写入模型价格缓存失败: model_name={model_name}, error={e}")

    return input_price, output_price


async def evict_model_pricing_cache_async(redis: Any, model_name: str) -> None:
    """
    清除模型价格缓存（异步）

    Args:
        redis: 异步 Redis 客户端
        model_name: 模型名称
    """
    if not model_name or not model_name.strip():
        return
    try:
        key = _cache_key(model_name)
        await redis.delete(key)
        logger.debug(f"已清除模型价格缓存: model_name={model_name}")
    except Exception as e:
        logger.warning(f"清除模型价格缓存失败: model_name={model_name}, error={e}")
