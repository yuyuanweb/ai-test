"""
Redis连接管理
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from redis import Redis as SyncRedis
from redis.asyncio import Redis
from app.core.config import get_settings

settings = get_settings()


def get_redis_client_sync() -> SyncRedis:
    """
    获取同步 Redis 客户端（供线程池中的 worker 使用，如进度推送）
    """
    return SyncRedis(
        host=settings.REDIS_HOST,
        port=settings.REDIS_PORT,
        db=settings.REDIS_DB,
        password=settings.REDIS_PASSWORD if settings.REDIS_PASSWORD else None,
        decode_responses=True,
        encoding='utf-8'
    )


async def get_redis_client() -> Redis:
    """
    获取 Redis 客户端
    """
    redis_client = Redis(
        host=settings.REDIS_HOST,
        port=settings.REDIS_PORT,
        db=settings.REDIS_DB,
        password=settings.REDIS_PASSWORD if settings.REDIS_PASSWORD else None,
        decode_responses=True,
        encoding='utf-8'
    )
    return redis_client
