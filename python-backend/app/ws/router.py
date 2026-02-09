"""
WebSocket 路由（SockJS + STOMP 兼容）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import re
import asyncio
import time
from fastapi import APIRouter, Request, WebSocket, WebSocketDisconnect
from starlette.responses import JSONResponse
from sqlalchemy import select

from app.ws.stomp_handler import StompSession, manager
from app.services.progress_service import PROGRESS_CHANNEL_PREFIX
from app.db.session import AsyncSessionLocal
from app.models.test_task import TestTask

router = APIRouter()


async def _send_task_status_on_subscribe(task_id: str) -> None:
    """
    订阅时立即推送任务当前状态（解决竞态：任务已完成但客户端尚未订阅）
    """
    try:
        async with AsyncSessionLocal() as db:
            result = await db.execute(
                select(TestTask).where(TestTask.id == task_id, TestTask.is_delete == 0)
            )
            task = result.scalar_one_or_none()
            if not task:
                return
            percentage = int((task.completed_subtasks / task.total_subtasks * 100)) if task.total_subtasks else 0
            progress_data = {
                "taskId": task_id,
                "percentage": percentage,
                "completedSubtasks": task.completed_subtasks,
                "totalSubtasks": task.total_subtasks,
                "status": task.status,
                "timestamp": int(time.time() * 1000)
            }
            await manager.broadcast_to_task(task_id, progress_data)
    except Exception as e:
        from app.core.logging_config import logger
        logger.warning("订阅时推送任务状态失败: taskId={}, error={}", task_id, str(e))


@router.get("/info")
async def sockjs_info(request: Request) -> JSONResponse:
    """SockJS info 端点（与 Spring 兼容，base_url 必须能让客户端正确构造 WebSocket URL）"""
    base = str(request.base_url).rstrip("/")
    if not base.endswith("api/ws"):
        base = f"{base}/api/ws"
    return JSONResponse({
        "base_url": base,
        "entropy": 123456,
        "origins": ["*:*"],
        "cookie_needed": False,
        "websocket": True,
    })


async def handle_websocket_session(websocket: WebSocket, session_id: str) -> None:
    """处理 WebSocket 会话"""
    await websocket.accept()
    session = StompSession(websocket)

    try:
        await session.send_sockjs_frame("o")

        while True:
            data = await websocket.receive_text()
            if not data:
                continue

            if data == "h":
                await session.send_sockjs_frame("h")
                continue

            if data.startswith("c"):
                break

            if data.startswith("a") or data.startswith("["):
                json_str = data[1:] if data.startswith("a") else data
                try:
                    messages = json.loads(json_str)
                    for msg in messages:
                        if isinstance(msg, str):
                            cmd, headers, body = session.parse_stomp_frame(msg)
                            if cmd == "CONNECT":
                                session.connected = True
                                version = headers.get("accept-version", "1.1")
                                heart_beat = headers.get("heart-beat", "0,0")
                                reply = f"CONNECTED\nversion:{version}\nheart-beat:{heart_beat}\n\n\x00"
                                await session.send_sockjs_frame("a", reply)
                            elif cmd == "SUBSCRIBE":
                                sub_id = headers.get("id", "")
                                dest = headers.get("destination", "")
                                manager.subscribe(session_id, session, sub_id, dest)
                                match = re.match(r"/topic/task/(.+)$", dest)
                                if match:
                                    task_id = match.group(1)
                                    asyncio.create_task(_send_task_status_on_subscribe(task_id))
                            elif cmd == "UNSUBSCRIBE":
                                sub_id = headers.get("id", "")
                                match = re.match(r"/topic/task/(.+)$", session.subscriptions.get(sub_id, ""))
                                task_id = match.group(1) if match else ""
                                if task_id:
                                    manager.unsubscribe(task_id, session_id, sub_id)
                                if sub_id in session.subscriptions:
                                    del session.subscriptions[sub_id]
                            elif cmd == "DISCONNECT":
                                break
                except (json.JSONDecodeError, KeyError) as e:
                    from app.core.logging_config import logger
                    logger.debug("解析 SockJS 消息失败: {}", str(e))

    except WebSocketDisconnect:
        pass
    except Exception as e:
        from app.core.logging_config import logger
        logger.warning("WebSocket 会话异常: session={}, error={}", session_id, str(e))
    finally:
        manager.remove_session(session_id)


@router.websocket("/{s1}/{s2}/websocket")
async def sockjs_websocket(websocket: WebSocket, s1: str, s2: str) -> None:
    """SockJS WebSocket 传输端点"""
    session_id = f"{s1}_{s2}"
    await handle_websocket_session(websocket, session_id)
