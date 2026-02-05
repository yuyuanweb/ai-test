"""
应用主入口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager

from app.core.config import get_settings
from app.core.errors import BusinessException
from app.core.logging_config import logger
from app.api import user, health, test
from app.middleware.session_middleware import RedisSessionMiddleware
from app.db.redis_session import RedisSessionBackend
from app.db.redis import get_redis_client

settings = get_settings()

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    应用生命周期管理
    """
    logger.info("应用启动")
    
    redis_client = await get_redis_client()
    session_backend = RedisSessionBackend(redis_client)
    
    app.state.redis_client = redis_client
    app.state.session_backend = session_backend
    
    yield
    
    if redis_client:
        await redis_client.close()
    
    logger.info("应用关闭")

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    docs_url="/api/docs",
    redoc_url="/api/redoc",
    openapi_url="/api/openapi.json",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.add_middleware(
    RedisSessionMiddleware,
    session_backend=None,
    cookie_name="session_id",
    max_age=settings.SESSION_EXPIRE_SECONDS
)


@app.exception_handler(BusinessException)
async def business_exception_handler(request: Request, exc: BusinessException):
    """
    业务异常处理器
    """
    return JSONResponse(
        status_code=200,
        content=exc.to_dict()
    )


app.include_router(health.router, prefix="/api")
app.include_router(user.router, prefix="/api")
app.include_router(test.router, prefix="/api")


if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=settings.APP_DEBUG,
        log_level="info"
    )
