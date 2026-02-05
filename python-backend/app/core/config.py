"""
配置管理模块
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """应用配置类"""
    
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = "11111111"
    DB_NAME: str = "ai_eval"
    
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_DB: int = 0
    REDIS_PASSWORD: str = ""
    
    OPENROUTER_API_KEY: str
    
    APP_NAME: str = "AI Evaluation Platform"
    APP_ENV: str = "local"
    APP_DEBUG: bool = True
    APP_PORT: int = 8123
    SECRET_KEY: str
    
    SESSION_EXPIRE_SECONDS: int = 2592000
    COOKIE_MAX_AGE: int = 2592000
    
    CORS_ORIGINS: str = "http://localhost:5173,http://localhost:8080"
    
    @property
    def DATABASE_URL(self) -> str:
        """构建数据库连接URL"""
        return f"mysql+pymysql://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"
    
    @property
    def ASYNC_DATABASE_URL(self) -> str:
        """构建异步数据库连接URL"""
        return f"mysql+asyncmy://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"
    
    @property
    def CORS_ORIGINS_LIST(self) -> list[str]:
        """解析CORS允许的源"""
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]
    
    class Config:
        env_file = ".env"
        case_sensitive = True
        extra = "ignore"


@lru_cache()
def get_settings() -> Settings:
    """获取配置实例（单例模式）"""
    return Settings()
