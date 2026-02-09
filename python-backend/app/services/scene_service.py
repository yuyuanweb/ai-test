"""
场景服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import uuid
from datetime import datetime
from typing import Optional, List, Any

from sqlalchemy import select, func, or_, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.scene import Scene
from app.models.scene_prompt import ScenePrompt
from app.core.errors import BusinessException, ErrorCode


class SceneService:
    """
    场景服务类
    """

    @staticmethod
    async def create_scene(db: AsyncSession, scene_data: dict, user_id: int) -> str:
        """
        创建场景

        Args:
            db: 数据库会话
            scene_data: 场景数据
            user_id: 用户ID

        Returns:
            场景ID

        Raises:
            BusinessException: 业务异常
        """
        if not scene_data.get("name") or not str(scene_data["name"]).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "场景名称不能为空")

        scene_id = str(uuid.uuid4())
        scene = Scene(
            id=scene_id,
            user_id=user_id,
            name=scene_data["name"].strip(),
            description=scene_data.get("description"),
            category=scene_data.get("category"),
            is_preset=0,
            is_active=1,
            is_delete=0
        )
        db.add(scene)
        await db.commit()

        return scene_id

    @staticmethod
    async def update_scene(db: AsyncSession, scene_data: dict, user_id: int) -> bool:
        """
        更新场景

        Args:
            db: 数据库会话
            scene_data: 场景数据
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        scene_id = scene_data.get("id")
        if not scene_id:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空")

        result = await db.execute(
            select(Scene).where(Scene.id == scene_id, Scene.is_delete == 0)
        )
        existing = result.scalar_one_or_none()
        if not existing:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        if existing.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        if scene_data.get("name") is not None:
            existing.name = scene_data["name"]
        if scene_data.get("description") is not None:
            existing.description = scene_data["description"]
        if scene_data.get("category") is not None:
            existing.category = scene_data["category"]
        if scene_data.get("is_active") is not None:
            existing.is_active = scene_data["is_active"]
        existing.update_time = datetime.now()

        await db.commit()
        return True

    @staticmethod
    async def delete_scene(db: AsyncSession, scene_id: str, user_id: int) -> bool:
        """
        删除场景（逻辑删除）

        Args:
            db: 数据库会话
            scene_id: 场景ID
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        result = await db.execute(
            select(Scene).where(Scene.id == scene_id, Scene.is_delete == 0)
        )
        scene = result.scalar_one_or_none()
        if not scene:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        if scene.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        scene.is_delete = 1
        await db.commit()
        return True

    @staticmethod
    async def get_scene(db: AsyncSession, scene_id: str, user_id: int) -> Optional[Scene]:
        """
        获取场景详情

        Args:
            db: 数据库会话
            scene_id: 场景ID
            user_id: 用户ID

        Returns:
            场景对象

        Raises:
            BusinessException: 业务异常
        """
        result = await db.execute(
            select(Scene).where(Scene.id == scene_id, Scene.is_delete == 0)
        )
        scene = result.scalar_one_or_none()
        if not scene:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        if scene.is_preset == 0 and scene.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看")

        return scene

    @staticmethod
    async def list_scenes(
        db: AsyncSession,
        user_id: int,
        page_num: int = 1,
        page_size: int = 10,
        category: Optional[str] = None,
        is_preset: Optional[bool] = None
    ) -> dict:
        """
        分页查询场景列表

        Args:
            db: 数据库会话
            user_id: 用户ID
            page_num: 页码
            page_size: 每页大小
            category: 分类过滤
            is_preset: 是否预设

        Returns:
            分页结果 {records, total, totalRow, pageNum, pageSize}
        """
        conditions = [
            Scene.is_delete == 0,
            or_(Scene.user_id == user_id, Scene.is_preset == 1)
        ]
        if category and category.strip():
            conditions.append(Scene.category == category.strip())
        if is_preset is not None:
            conditions.append(Scene.is_preset == (1 if is_preset else 0))

        count_result = await db.execute(
            select(func.count()).select_from(Scene).where(and_(*conditions))
        )
        total = count_result.scalar() or 0

        offset = (page_num - 1) * page_size
        result = await db.execute(
            select(Scene)
            .where(and_(*conditions))
            .order_by(Scene.create_time.desc())
            .offset(offset)
            .limit(page_size)
        )
        records = result.scalars().all()

        return {
            "records": [SceneService._scene_to_dict(r) for r in records],
            "total": total,
            "totalRow": total,
            "pageNum": page_num,
            "pageSize": page_size
        }

    @staticmethod
    async def get_scene_prompts(db: AsyncSession, scene_id: str, user_id: int) -> List[ScenePrompt]:
        """
        获取场景下的所有提示词

        Args:
            db: 数据库会话
            scene_id: 场景ID
            user_id: 用户ID

        Returns:
            提示词列表

        Raises:
            BusinessException: 业务异常
        """
        scene = await SceneService.get_scene(db, scene_id, user_id)
        if not scene:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        result = await db.execute(
            select(ScenePrompt)
            .where(ScenePrompt.scene_id == scene_id, ScenePrompt.is_delete == 0)
            .order_by(ScenePrompt.prompt_index.asc())
        )
        return list(result.scalars().all())

    @staticmethod
    async def add_scene_prompt(db: AsyncSession, request_data: dict, user_id: int) -> str:
        """
        添加场景提示词

        Args:
            db: 数据库会话
            request_data: 请求数据
            user_id: 用户ID

        Returns:
            提示词ID

        Raises:
            BusinessException: 业务异常
        """
        scene_id = request_data.get("scene_id") or request_data.get("sceneId")
        if not scene_id or not str(scene_id).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空")

        scene_result = await db.execute(
            select(Scene).where(Scene.id == scene_id.strip(), Scene.is_delete == 0)
        )
        scene = scene_result.scalar_one_or_none()
        if not scene:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        if scene.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        count_result = await db.execute(
            select(func.count()).select_from(ScenePrompt).where(
                ScenePrompt.scene_id == scene_id.strip(), ScenePrompt.is_delete == 0
            )
        )
        prompt_index = count_result.scalar() or 0

        title = request_data.get("title") or ""
        content = request_data.get("content") or ""
        if not title.strip() or not content.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词标题和内容不能为空")

        prompt_id = str(uuid.uuid4())
        prompt = ScenePrompt(
            id=prompt_id,
            scene_id=scene_id.strip(),
            user_id=user_id,
            prompt_index=prompt_index,
            title=title.strip(),
            content=content.strip(),
            difficulty=request_data.get("difficulty"),
            expected_output=request_data.get("expected_output") or request_data.get("expectedOutput"),
            is_delete=0
        )
        db.add(prompt)
        await db.commit()
        return prompt_id

    @staticmethod
    async def update_scene_prompt(db: AsyncSession, request_data: dict, user_id: int) -> bool:
        """
        更新场景提示词

        Args:
            db: 数据库会话
            request_data: 请求数据
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        prompt_id = request_data.get("id")
        if not prompt_id or not str(prompt_id).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词ID不能为空")

        result = await db.execute(
            select(ScenePrompt).where(ScenePrompt.id == prompt_id.strip(), ScenePrompt.is_delete == 0)
        )
        existing = result.scalar_one_or_none()
        if not existing:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "提示词不存在")

        if existing.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        if request_data.get("title") is not None:
            existing.title = request_data["title"]
        if request_data.get("content") is not None:
            existing.content = request_data["content"]
        if request_data.get("difficulty") is not None:
            existing.difficulty = request_data["difficulty"]
        if "expected_output" in request_data or "expectedOutput" in request_data:
            val = request_data.get("expected_output") or request_data.get("expectedOutput")
            existing.expected_output = val
        existing.update_time = datetime.now()

        await db.commit()
        return True

    @staticmethod
    async def delete_scene_prompt(db: AsyncSession, prompt_id: str, user_id: int) -> bool:
        """
        删除场景提示词（逻辑删除）

        Args:
            db: 数据库会话
            prompt_id: 提示词ID
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        result = await db.execute(
            select(ScenePrompt).where(ScenePrompt.id == prompt_id.strip(), ScenePrompt.is_delete == 0)
        )
        prompt = result.scalar_one_or_none()
        if not prompt:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "提示词不存在")

        if prompt.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        prompt.is_delete = 1
        await db.commit()
        return True

    @staticmethod
    def _scene_prompt_to_dict(prompt: ScenePrompt) -> dict:
        """将 ScenePrompt 转为前端需要的字典格式"""
        return {
            "id": prompt.id,
            "sceneId": prompt.scene_id,
            "userId": prompt.user_id,
            "promptIndex": prompt.prompt_index,
            "title": prompt.title,
            "content": prompt.content,
            "difficulty": prompt.difficulty,
            "tags": prompt.tags,
            "expectedOutput": prompt.expected_output,
            "createTime": prompt.create_time.isoformat() if prompt.create_time else None,
            "updateTime": prompt.update_time.isoformat() if prompt.update_time else None,
        }

    @staticmethod
    def _scene_to_dict(scene: Scene) -> dict:
        """将Scene对象转为前端需要的字典格式"""
        return {
            "id": scene.id,
            "userId": scene.user_id,
            "name": scene.name,
            "description": scene.description,
            "category": scene.category,
            "isPreset": scene.is_preset,
            "isActive": scene.is_active,
            "createTime": scene.create_time.isoformat() if scene.create_time else None,
            "updateTime": scene.update_time.isoformat() if scene.update_time else None
        }
