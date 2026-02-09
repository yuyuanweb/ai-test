"""
应用配置
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import os
from functools import lru_cache
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """
    应用配置类
    """
    APP_NAME: str = "AI评测平台"
    APP_VERSION: str = "1.0.0"
    APP_DEBUG: bool = True
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 9090
    
    DATABASE_URL: str = "mysql+aiomysql://root:123456@localhost:3306/ai_eval"

    @property
    def sync_database_url(self) -> str:
        """同步数据库连接 URL（mysql+pymysql，供 sync_session 使用）"""
        url = self.DATABASE_URL
        if "aiomysql" in url:
            return url.replace("mysql+aiomysql", "mysql+pymysql", 1)
        if "asyncpg" in url:
            return url.replace("postgresql+asyncpg", "postgresql+psycopg2", 1)
        return url

    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_DB: int = 0
    REDIS_PASSWORD: str = ""
    
    OPENROUTER_API_KEY: str = ""
    OPENROUTER_BASE_URL: str = "https://openrouter.ai/api/v1"
    
    SESSION_SECRET_KEY: str = "your-secret-key-here"
    SESSION_MAX_AGE: int = 86400
    SESSION_EXPIRE_SECONDS: int = 86400
    
    CORS_ORIGINS: str = "http://localhost:5173,http://localhost:3000"
    
    @property
    def cors_origins_list(self) -> list:
        """获取 CORS origins 列表"""
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]
    
    class Config:
        env_file = ".env"
        case_sensitive = True
        extra = "ignore"


@lru_cache()
def get_settings() -> Settings:
    """
    获取配置实例（单例模式）
    """
    return Settings()
