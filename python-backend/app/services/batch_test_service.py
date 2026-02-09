"""
批量测试服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import json
import time
import uuid
from datetime import datetime
from typing import Optional, List
from decimal import Decimal

from sqlalchemy import select, func, or_, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.test_task import TestTask
from app.models.test_result import TestResult
from app.models.scene import Scene
from app.models.scene_prompt import ScenePrompt
from app.core.errors import BusinessException, ErrorCode
from app.core.logging_config import logger


class BatchTestService:
    """
    批量测试服务类
    """

    @staticmethod
    async def create_batch_test_task(db: AsyncSession, request_data: dict, user_id: int) -> str:
        """
        创建批量测试任务

        Args:
            db: 数据库会话
            request_data: 请求数据
            user_id: 用户ID

        Returns:
            任务ID

        Raises:
            BusinessException: 业务异常
        """
        scene_id = request_data.get("scene_id") or request_data.get("sceneId")
        if not scene_id or not str(scene_id).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空")

        models = request_data.get("models")
        if not models or not isinstance(models, list) or len(models) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空")

        scene_result = await db.execute(
            select(Scene).where(Scene.id == scene_id.strip(), Scene.is_delete == 0)
        )
        scene = scene_result.scalar_one_or_none()
        if not scene:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在")

        if scene.is_preset == 0 and scene.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限使用该场景")

        prompts_result = await db.execute(
            select(ScenePrompt)
            .where(ScenePrompt.scene_id == scene_id.strip(), ScenePrompt.is_delete == 0)
            .order_by(ScenePrompt.prompt_index.asc())
        )
        prompts = list(prompts_result.scalars().all())
        if not prompts:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "场景中没有提示词")

        task_id = str(uuid.uuid4())
        total_subtasks = len(models) * len(prompts)

        config_map = {}
        if request_data.get("temperature") is not None:
            config_map["temperature"] = request_data["temperature"]
        if request_data.get("top_p") is not None:
            config_map["topP"] = request_data["top_p"]
        if request_data.get("topP") is not None:
            config_map["topP"] = request_data["topP"]
        if request_data.get("max_tokens") is not None:
            config_map["maxTokens"] = request_data["max_tokens"]
        if request_data.get("maxTokens") is not None:
            config_map["maxTokens"] = request_data["maxTokens"]
        if request_data.get("top_k") is not None:
            config_map["topK"] = request_data["top_k"]
        if request_data.get("topK") is not None:
            config_map["topK"] = request_data["topK"]
        if request_data.get("frequency_penalty") is not None:
            config_map["frequencyPenalty"] = request_data["frequency_penalty"]
        if request_data.get("frequencyPenalty") is not None:
            config_map["frequencyPenalty"] = request_data["frequencyPenalty"]
        if request_data.get("presence_penalty") is not None:
            config_map["presencePenalty"] = request_data["presence_penalty"]
        if request_data.get("presencePenalty") is not None:
            config_map["presencePenalty"] = request_data["presencePenalty"]
        if request_data.get("enable_ai_scoring") is not None:
            config_map["enableAiScoring"] = request_data["enable_ai_scoring"]
        if request_data.get("enableAiScoring") is not None:
            config_map["enableAiScoring"] = request_data["enableAiScoring"]
        config_json = json.dumps(config_map) if config_map else None

        models_json = json.dumps(models)

        task = TestTask(
            id=task_id,
            user_id=user_id,
            name=request_data.get("name"),
            scene_id=scene_id.strip(),
            models=models_json,
            config=config_json,
            status="pending",
            total_subtasks=total_subtasks,
            completed_subtasks=0,
            is_delete=0
        )
        db.add(task)
        await db.commit()

        logger.info(
            "创建批量测试任务: taskId={}, sceneId={}, models={}, prompts={}, totalSubtasks={}",
            task_id, scene_id, len(models), len(prompts), total_subtasks
        )

        from app.services.progress_service import publish_progress
        from app.services.batch_test_worker import run_subtask_sync

        publish_progress(task_id, {
            "taskId": task_id,
            "percentage": 0,
            "completedSubtasks": 0,
            "totalSubtasks": total_subtasks,
            "status": "pending",
            "timestamp": int(time.time() * 1000)
        })

        for model_name in models:
            for prompt in prompts:
                sub_task_data = {
                    "taskId": task_id,
                    "sceneId": scene_id.strip(),
                    "promptId": prompt.id,
                    "promptTitle": prompt.title,
                    "promptContent": prompt.content,
                    "modelName": model_name,
                    "userId": user_id
                }
                asyncio.create_task(asyncio.to_thread(run_subtask_sync, sub_task_data))

        return task_id

    @staticmethod
    async def get_task(db: AsyncSession, task_id: str, user_id: int) -> TestTask:
        """
        获取任务详情

        Args:
            db: 数据库会话
            task_id: 任务ID
            user_id: 用户ID

        Returns:
            任务对象

        Raises:
            BusinessException: 业务异常
        """
        result = await db.execute(
            select(TestTask).where(TestTask.id == task_id.strip(), TestTask.is_delete == 0)
        )
        task = result.scalar_one_or_none()
        if not task:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在")

        if task.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看")

        return task

    @staticmethod
    async def list_tasks(
        db: AsyncSession,
        user_id: int,
        query_request: dict
    ) -> dict:
        """
        分页查询任务列表

        Args:
            db: 数据库会话
            user_id: 用户ID
            query_request: 查询参数

        Returns:
            分页结果
        """
        conditions = [TestTask.user_id == user_id, TestTask.is_delete == 0]

        category = query_request.get("category")
        if category and str(category).strip():
            scene_result = await db.execute(
                select(Scene.id).where(Scene.category == str(category).strip(), Scene.is_delete == 0)
            )
            scene_ids = [row[0] for row in scene_result.fetchall()]
            if scene_ids:
                conditions.append(TestTask.scene_id.in_(scene_ids))
            else:
                conditions.append(TestTask.id == "impossible")

        status = query_request.get("status")
        if status and str(status).strip():
            conditions.append(TestTask.status == str(status).strip())

        keyword = query_request.get("keyword")
        if keyword and str(keyword).strip():
            conditions.append(TestTask.name.like(f"%{str(keyword).strip()}%"))

        start_time = query_request.get("start_time") or query_request.get("startTime")
        if start_time and str(start_time).strip():
            try:
                dt = datetime.strptime(str(start_time).strip(), "%Y-%m-%d %H:%M:%S")
                conditions.append(TestTask.create_time >= dt)
            except ValueError:
                logger.warning("解析开始时间失败: %s", start_time)

        end_time = query_request.get("end_time") or query_request.get("endTime")
        if end_time and str(end_time).strip():
            try:
                dt = datetime.strptime(str(end_time).strip(), "%Y-%m-%d %H:%M:%S")
                conditions.append(TestTask.create_time <= dt)
            except ValueError:
                logger.warning("解析结束时间失败: %s", end_time)

        page_num = query_request.get("page_num") or query_request.get("pageNum") or 1
        page_size = query_request.get("page_size") or query_request.get("pageSize") or 10

        count_result = await db.execute(
            select(func.count()).select_from(TestTask).where(and_(*conditions))
        )
        total = count_result.scalar() or 0

        offset = (page_num - 1) * page_size
        result = await db.execute(
            select(TestTask)
            .where(and_(*conditions))
            .order_by(TestTask.create_time.desc())
            .offset(offset)
            .limit(page_size)
        )
        records = result.scalars().all()

        return {
            "records": [BatchTestService._task_to_dict(t) for t in records],
            "total": total,
            "totalRow": total,
            "pageNum": page_num,
            "pageSize": page_size
        }

    @staticmethod
    async def delete_task(db: AsyncSession, task_id: str, user_id: int) -> bool:
        """
        删除任务

        Args:
            db: 数据库会话
            task_id: 任务ID
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        task = await BatchTestService.get_task(db, task_id, user_id)
        task.is_delete = 1
        await db.commit()
        return True

    @staticmethod
    async def get_task_results(db: AsyncSession, task_id: str, user_id: int) -> List[dict]:
        """
        获取任务的测试结果

        Args:
            db: 数据库会话
            task_id: 任务ID
            user_id: 用户ID

        Returns:
            测试结果列表

        Raises:
            BusinessException: 业务异常
        """
        await BatchTestService.get_task(db, task_id, user_id)

        result = await db.execute(
            select(TestResult)
            .where(TestResult.task_id == task_id.strip(), TestResult.is_delete == 0)
            .order_by(TestResult.create_time.asc())
        )
        results = result.scalars().all()

        return [BatchTestService._result_to_dict(r) for r in results]

    @staticmethod
    async def update_test_result_rating(
        db: AsyncSession,
        result_id: str,
        user_rating: Optional[int],
        user_id: int
    ) -> bool:
        """
        更新测试结果评分

        Args:
            db: 数据库会话
            result_id: 测试结果ID
            user_rating: 用户评分(1-5)
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        if not result_id or not str(result_id).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "测试结果ID不能为空")

        if user_rating is not None and (user_rating < 1 or user_rating > 5):
            raise BusinessException(ErrorCode.PARAMS_ERROR, "评分必须在1-5之间")

        result = await db.execute(
            select(TestResult).where(TestResult.id == result_id.strip(), TestResult.is_delete == 0)
        )
        test_result = result.scalar_one_or_none()
        if not test_result:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "测试结果不存在")

        if test_result.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该测试结果")

        test_result.user_rating = user_rating
        test_result.update_time = datetime.now()
        await db.commit()

        logger.info("更新测试结果评分: resultId=%s, userId=%s, rating=%s", result_id, user_id, user_rating)
        return True

    @staticmethod
    def _task_to_dict(task: TestTask) -> dict:
        """将TestTask对象转为前端需要的字典格式"""
        cost_val = task.cost if hasattr(task, 'cost') else None
        return {
            "id": task.id,
            "userId": task.user_id,
            "name": task.name,
            "sceneId": task.scene_id,
            "models": task.models,
            "config": task.config,
            "status": task.status,
            "totalSubtasks": task.total_subtasks,
            "completedSubtasks": task.completed_subtasks,
            "startedAt": task.started_at.isoformat() if task.started_at else None,
            "completedAt": task.completed_at.isoformat() if task.completed_at else None,
            "createTime": task.create_time.isoformat() if task.create_time else None,
            "updateTime": task.update_time.isoformat() if task.update_time else None
        }

    @staticmethod
    def _result_to_dict(result: TestResult) -> dict:
        """将TestResult对象转为前端需要的字典格式"""
        cost_val = float(result.cost) if result.cost is not None else None
        return {
            "id": result.id,
            "taskId": result.task_id,
            "userId": result.user_id,
            "sceneId": result.scene_id,
            "promptId": result.prompt_id,
            "modelName": result.model_name,
            "inputPrompt": result.input_prompt,
            "outputText": result.output_text,
            "reasoning": result.reasoning,
            "responseTimeMs": result.response_time_ms,
            "inputTokens": result.input_tokens,
            "outputTokens": result.output_tokens,
            "cost": cost_val,
            "userRating": result.user_rating,
            "aiScore": result.ai_score,
            "createTime": result.create_time.isoformat() if result.create_time else None,
            "updateTime": result.update_time.isoformat() if result.update_time else None
        }
