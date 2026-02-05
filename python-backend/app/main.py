"""
FastAPI主应用
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import get_settings
from app.core.logging_config import LoggingConfig
from app.api import health

settings = get_settings()

LoggingConfig.setup_logging(log_level="DEBUG" if settings.APP_DEBUG else "INFO")

logger = logging.getLogger(__name__)

app = FastAPI(
    title=settings.APP_NAME,
    description="AI大模型评测平台 - Python后端",
    version="0.0.1",
    docs_url="/api/docs",
    redoc_url="/api/redoc",
    openapi_url="/api/openapi.json"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS_LIST,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api")


@app.get("/")
async def root():
    """根路径"""
    logger.info("访问根路径")
    return {
        "message": "Welcome to AI Evaluation Platform",
        "docs": "/api/docs",
        "version": "0.0.1"
    }


@app.on_event("startup")
async def startup_event():
    """应用启动事件"""
    logger.info("=" * 50)
    logger.info("AI 大模型评测平台启动")
    logger.info("应用名称: %s", settings.APP_NAME)
    logger.info("环境: %s", settings.APP_ENV)
    logger.info("端口: %d", settings.APP_PORT)
    logger.info("调试模式: %s", settings.APP_DEBUG)
    logger.info("=" * 50)


@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭事件"""
    logger.info("AI 大模型评测平台关闭")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.APP_PORT,
        reload=settings.APP_DEBUG
    )
