"""
进度推送服务（进程内直接推送 + Redis Pub/Sub 备用）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import json
from typing import Any, Optional

from app.db.redis import get_redis_client_sync

PROGRESS_CHANNEL_PREFIX = "task:progress:"

_main_loop: Optional[asyncio.AbstractEventLoop] = None


def set_event_loop(loop: asyncio.AbstractEventLoop) -> None:
    """设置主事件循环（供进程内直接推送使用）"""
    global _main_loop
    _main_loop = loop


def publish_progress(task_id: str, progress_data: dict) -> None:
    """
    发布任务进度（进程内直接推送到 WebSocket，与 Java 同进程模式一致）

    Args:
        task_id: 任务ID
        progress_data: 进度数据，包含 percentage, completedSubtasks, totalSubtasks, status 等
    """
    from app.ws.stomp_handler import manager
    from app.core.logging_config import logger

    if _main_loop and _main_loop.is_running():
        try:
            future = asyncio.run_coroutine_threadsafe(
                manager.broadcast_to_task(task_id, progress_data),
                _main_loop
            )
            future.result(timeout=2)
        except Exception as e:
            logger.warning("进程内进度推送失败: taskId={}, error={}", task_id, str(e))

    try:
        redis_client = get_redis_client_sync()
        if redis_client:
            channel = f"{PROGRESS_CHANNEL_PREFIX}{task_id}"
            redis_client.publish(channel, json.dumps(progress_data))
    except Exception as e:
        from app.core.logging_config import logger
        logger.warning("Redis 进度发布失败: taskId={}, error={}", task_id, str(e))
