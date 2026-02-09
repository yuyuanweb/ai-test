"""
STOMP over WebSocket 处理器（兼容 SockJS 格式）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import re
import asyncio
from typing import Dict, Set, Callable, Any
from collections import defaultdict

from starlette.websockets import WebSocket

from app.core.logging_config import logger


class StompSession:
    """STOMP 会话管理"""

    def __init__(self, websocket: WebSocket):
        self.websocket = websocket
        self.subscriptions: Dict[str, str] = {}
        self.connected = False

    async def send_sockjs_frame(self, frame_type: str, data: str = "") -> None:
        """发送 SockJS 格式帧"""
        if frame_type == "o":
            await self.websocket.send_text("o")
        elif frame_type == "a":
            await self.websocket.send_text("a" + json.dumps([data]))
        elif frame_type == "h":
            await self.websocket.send_text("h")

    async def send_stomp_message(self, destination: str, body: str, subscription_id: str = "") -> None:
        """发送 STOMP MESSAGE 帧"""
        headers = f"destination:{destination}\n"
        if subscription_id:
            headers += f"subscription:{subscription_id}\n"
        headers += "content-type:application/json\n"
        frame = f"MESSAGE\n{headers}\n\n{body}\x00"
        await self.send_sockjs_frame("a", frame)

    def parse_stomp_frame(self, data: str) -> tuple[str, dict, str]:
        """解析 STOMP 帧，返回 (command, headers, body)"""
        if not data or "\x00" not in data:
            return ("", {}, "")
        parts = data.split("\n\n", 1)
        if len(parts) < 2:
            return ("", {}, "")
        header_part = parts[0]
        body = parts[1].rstrip("\x00") if len(parts) > 1 else ""
        lines = header_part.split("\n")
        command = lines[0] if lines else ""
        headers = {}
        for line in lines[1:]:
            if ":" in line:
                k, v = line.split(":", 1)
                headers[k.strip()] = v.strip()
        return (command, headers, body)


class ConnectionManager:
    """WebSocket 连接管理器"""

    def __init__(self):
        self.active_connections: Dict[str, Dict[str, StompSession]] = defaultdict(dict)

    def subscribe(self, session_id: str, session: StompSession, sub_id: str, destination: str) -> None:
        """订阅任务进度，从 destination /topic/task/{taskId} 提取 task_id"""
        match = re.match(r"/topic/task/(.+)$", destination)
        task_id = match.group(1) if match else destination
        key = f"{session_id}_{sub_id}"
        self.active_connections[task_id][key] = session
        session.subscriptions[sub_id] = destination

    def unsubscribe(self, task_id: str, session_id: str, sub_id: str) -> None:
        """取消订阅"""
        key = f"{session_id}_{sub_id}"
        if task_id in self.active_connections and key in self.active_connections[task_id]:
            del self.active_connections[task_id][key]

    def remove_session(self, session_id: str) -> None:
        """移除会话的所有订阅"""
        for task_id in list(self.active_connections.keys()):
            to_remove = [k for k in self.active_connections[task_id] if k.startswith(session_id + "_")]
            for k in to_remove:
                del self.active_connections[task_id][k]

    async def broadcast_to_task(self, task_id: str, message: dict) -> None:
        """向订阅该任务的所有连接广播"""
        key = str(task_id)
        if key not in self.active_connections:
            return
        body = json.dumps(message)
        destination = f"/topic/task/{key}"
        for conn_key, session in list(self.active_connections[key].items()):
            try:
                for sub_id, dest in session.subscriptions.items():
                    if dest == destination:
                        await session.send_stomp_message(destination, body, sub_id)
                        break
            except Exception as e:
                logger.warning("广播消息失败: key={}, error={}", conn_key, str(e))


manager = ConnectionManager()
