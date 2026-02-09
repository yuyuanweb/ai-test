"""
Celery 应用配置
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from celery import Celery
from app.core.config import get_settings

settings = get_settings()

celery_app = Celery(
    "ai_eval",
    broker=settings.rabbitmq_url,
    backend=f"redis://{settings.REDIS_HOST}:{settings.REDIS_PORT}/{settings.REDIS_DB}",
    include=["app.tasks.batch_test_tasks"]
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=600,
    worker_prefetch_multiplier=5,
    task_acks_late=True,
    task_reject_on_worker_lost=True,
)
