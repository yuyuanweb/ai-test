"""
应用配置
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from functools import lru_cache
from urllib.parse import quote_plus

from pydantic import ConfigDict, computed_field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """
    应用配置类
    支持两种方式配置数据库：直接配置 DATABASE_URL，或配置 DB_HOST/DB_USER/DB_PASSWORD 等（与 Java 端 .env 一致）。
    """
    APP_NAME: str = "AI评测平台"
    APP_VERSION: str = "1.0.0"
    APP_DEBUG: bool = True
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 9090

    DATABASE_URL: str = ""
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = ""
    DB_NAME: str = "ai_eval"

    @computed_field
    @property
    def effective_database_url(self) -> str:
        """实际使用的数据库 URL：若配置了 DB_* 则用其拼接，否则用 DATABASE_URL"""
        if self.DATABASE_URL:
            return self.DATABASE_URL
        password = quote_plus(self.DB_PASSWORD) if self.DB_PASSWORD else ""
        return f"mysql+aiomysql://{self.DB_USER}:{password}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"

    @property
    def sync_database_url(self) -> str:
        """同步数据库连接 URL（mysql+pymysql，供 sync_session 使用）"""
        url = self.effective_database_url
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

    TENCENT_COS_ACCESS_KEY: str = ""
    TENCENT_COS_SECRET_KEY: str = ""
    TENCENT_COS_REGION: str = "ap-guangzhou"
    TENCENT_COS_BUCKET: str = "yupi-1300582479"
    TENCENT_COS_HOST: str = ""

    @property
    def effective_cos_host(self) -> str:
        """COS 访问域名：优先用 TENCENT_COS_HOST，否则由 bucket+region 推导（与 Java tencent.cos 一致）"""
        if self.TENCENT_COS_HOST and self.TENCENT_COS_HOST.strip():
            return self.TENCENT_COS_HOST.rstrip("/")
        if self.TENCENT_COS_BUCKET and self.TENCENT_COS_REGION:
            return f"https://{self.TENCENT_COS_BUCKET}.cos.{self.TENCENT_COS_REGION}.myqcloud.com"
        return ""

    @property
    def cors_origins_list(self) -> list:
        """获取 CORS origins 列表"""
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]
    
    model_config = ConfigDict(
        env_file=".env",
        case_sensitive=True,
        extra="ignore",
    )


@lru_cache()
def get_settings() -> Settings:
    """
    获取配置实例（单例模式）
    """
    return Settings()
