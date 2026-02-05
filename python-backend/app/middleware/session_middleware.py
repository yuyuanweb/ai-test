"""
自定义Session中间件(基于Redis)
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Callable
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.types import ASGIApp
from app.db.redis_session import RedisSessionBackend
from app.core.config import get_settings

settings = get_settings()


class RedisSessionMiddleware(BaseHTTPMiddleware):
    """
    基于Redis的Session中间件
    """
    
    def __init__(
        self,
        app: ASGIApp,
        session_backend: RedisSessionBackend,
        cookie_name: str = "session_id",
        max_age: int = None,
        same_site: str = "lax",
        https_only: bool = False
    ):
        super().__init__(app)
        self.session_backend = session_backend
        self.cookie_name = cookie_name
        self.max_age = max_age or settings.SESSION_MAX_AGE
        self.same_site = same_site
        self.https_only = https_only
    
    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """
        处理请求
        """
        try:
            session_backend = self.session_backend
            if session_backend is None:
                session_backend = getattr(request.app.state, 'session_backend', None)
            
            if session_backend is None:
                request.state.session = {}
                return await call_next(request)
            
            session_id = request.cookies.get(self.cookie_name)
            
            if session_id and await session_backend.exists(session_id):
                session_data = await session_backend.get(session_id)
                request.state.session_id = session_id
                request.state.session = session_data or {}
            else:
                session_id = session_backend.generate_session_id()
                request.state.session_id = session_id
                request.state.session = {}
            
            class SessionProxy(dict):
                """Session代理类，支持字典操作"""
                def __init__(self, data, backend, session_id):
                    super().__init__(data or {})
                    self._backend = backend
                    self._session_id = session_id
                    self._modified = False
                
                def __setitem__(self, key, value):
                    super().__setitem__(key, value)
                    self._modified = True
                
                def __delitem__(self, key):
                    super().__delitem__(key)
                    self._modified = True
                
                def pop(self, key, default=None):
                    self._modified = True
                    return super().pop(key, default)
                
                def clear(self):
                    self._modified = True
                    return super().clear()
                
                async def save(self):
                    """保存Session到Redis"""
                    if self._modified or len(self) > 0:
                        await self._backend.set(self._session_id, dict(self))
            
            session_proxy = SessionProxy(
                request.state.session,
                session_backend,
                session_id
            )
            request.state.session = session_proxy
            
            response = await call_next(request)
            
            await session_proxy.save()
            
            response.set_cookie(
                key=self.cookie_name,
                value=session_id,
                max_age=self.max_age,
                httponly=True,
                secure=self.https_only,
                samesite=self.same_site,
                path="/"
            )
            
            return response
        except Exception as e:
            import logging
            logger = logging.getLogger(__name__)
            logger.error(f"Session中间件错误: {str(e)}", exc_info=True)
            raise
