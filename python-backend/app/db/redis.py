"""
Redis连接管理
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import redis
from app.core.config import get_settings

settings = get_settings()

redis_client = redis.Redis(
    host=settings.REDIS_HOST,
    port=settings.REDIS_PORT,
    db=settings.REDIS_DB,
    password=settings.REDIS_PASSWORD if settings.REDIS_PASSWORD else None,
    decode_responses=True
)


def get_redis():
    """获取Redis客户端"""
    return redis_client
