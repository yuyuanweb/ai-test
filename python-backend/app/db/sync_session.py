"""
同步数据库会话（用于 Celery Worker）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from app.core.config import get_settings

settings = get_settings()

sync_engine = create_engine(
    settings.sync_database_url,
    echo=settings.APP_DEBUG,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10
)

SyncSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=sync_engine)


def get_sync_session() -> Session:
    """获取同步数据库会话"""
    return SyncSessionLocal()
