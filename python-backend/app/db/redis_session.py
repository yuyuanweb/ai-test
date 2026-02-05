"""
Redis Session存储实现
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import uuid
from typing import Any, Dict, Optional
from redis.asyncio import Redis
from app.core.config import get_settings

settings = get_settings()


class RedisSessionBackend:
    """
    Redis Session后端存储
    """
    
    def __init__(self, redis_client: Redis):
        self.redis = redis_client
        self.prefix = "session:"
        self.expire = settings.SESSION_EXPIRE_SECONDS
    
    def _get_key(self, session_id: str) -> str:
        """
        获取Redis键名
        """
        return f"{self.prefix}{session_id}"
    
    async def get(self, session_id: str) -> Optional[Dict[str, Any]]:
        """
        获取Session数据
        """
        key = self._get_key(session_id)
        data = await self.redis.get(key)
        if data:
            return json.loads(data)
        return None
    
    async def set(self, session_id: str, data: Dict[str, Any]) -> None:
        """
        设置Session数据
        """
        key = self._get_key(session_id)
        await self.redis.setex(
            key,
            self.expire,
            json.dumps(data)
        )
    
    async def delete(self, session_id: str) -> None:
        """
        删除Session数据
        """
        key = self._get_key(session_id)
        await self.redis.delete(key)
    
    async def exists(self, session_id: str) -> bool:
        """
        检查Session是否存在
        """
        key = self._get_key(session_id)
        return await self.redis.exists(key) > 0
    
    def generate_session_id(self) -> str:
        """
        生成Session ID
        """
        return str(uuid.uuid4())
