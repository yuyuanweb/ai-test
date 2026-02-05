"""
健康检查接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text

from app.db.session import get_db
from app.core.logging_config import logger

router = APIRouter(prefix="/health", tags=["健康检查"])


@router.get("", summary="基础健康检查")
async def health_check():
    """
    基础健康检查接口
    """
    logger.info("健康检查")
    return {
        "status": "ok",
        "message": "AI Evaluation Platform is running"
    }


@router.get("/db", summary="数据库健康检查")
async def db_health_check(db: AsyncSession = Depends(get_db)):
    """
    数据库健康检查
    """
    try:
        result = await db.execute(text("SELECT 1"))
        result.scalar()
        logger.info("数据库连接正常")
        return {
            "status": "ok",
            "message": "Database connection is healthy"
        }
    except Exception as e:
        logger.error(f"数据库连接失败: {str(e)}")
        return {
            "status": "error",
            "message": f"Database connection failed: {str(e)}"
        }
