"""
批量测试 Celery 任务
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import uuid
import time
from datetime import datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import select, text
from openai import OpenAI

from app.core.celery_app import celery_app
from app.core.config import get_settings
from app.db.sync_session import get_sync_session
from app.models.test_task import TestTask
from app.models.test_result import TestResult
from app.models.model import Model
from app.utils.cost_calculator import CostCalculator

settings = get_settings()
DEFAULT_TEMPERATURE = 0.7
DEFAULT_INPUT_PRICE = Decimal("1")
DEFAULT_OUTPUT_PRICE = Decimal("2")
TOKENS_PER_MILLION = 1_000_000


def run_subtask_sync(sub_task_data: dict) -> dict:
    """
    同步执行单个子任务（在线程池中运行，进程内执行，无需 Celery Worker）
    """
    return _do_process_subtask(sub_task_data)


def _do_process_subtask(sub_task_data: dict) -> dict:
    """
    处理批量测试子任务核心逻辑
    """
    task_id = sub_task_data.get("taskId")
    model_name = sub_task_data.get("modelName")
    prompt_title = sub_task_data.get("promptTitle")
    prompt_content = sub_task_data.get("promptContent")
    user_id = sub_task_data.get("userId")

    start_time = time.time()
    result_id = str(uuid.uuid4())

    session = get_sync_session()
    try:
        task_result = session.execute(
            select(TestTask).where(TestTask.id == task_id, TestTask.is_delete == 0)
        )
        task = task_result.scalar_one_or_none()
        if not task:
            return {"error": "任务不存在", "taskId": task_id}

        if task.status in ("cancelled", "failed"):
            return {"skipped": True, "taskId": task_id, "status": task.status}

        config = {}
        if task.config:
            try:
                config = json.loads(task.config)
            except json.JSONDecodeError:
                pass

        temperature = config.get("temperature", DEFAULT_TEMPERATURE)
        if isinstance(temperature, (int, float)):
            pass
        else:
            temperature = DEFAULT_TEMPERATURE

        max_tokens = config.get("maxTokens", 4096)
        if isinstance(max_tokens, (int, float)):
            max_tokens = int(max_tokens)
        else:
            max_tokens = 4096

        client = OpenAI(
            api_key=settings.OPENROUTER_API_KEY,
            base_url="https://openrouter.ai/api/v1"
        )

        response = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": prompt_content}],
            temperature=float(temperature),
            max_tokens=max_tokens,
            extra_headers={
                "HTTP-Referer": "https://codefather.cn",
                "X-Title": "AI Evaluation Platform"
            }
        )

        response_time_ms = int((time.time() - start_time) * 1000)
        output_text = ""
        input_tokens = 0
        output_tokens = 0

        if response.choices and len(response.choices) > 0:
            output_text = response.choices[0].message.content or ""
            if hasattr(response.choices[0].message, "reasoning_content") and response.choices[0].message.reasoning_content:
                output_text = (response.choices[0].message.reasoning_content or "") + output_text

        if response.usage:
            input_tokens = response.usage.prompt_tokens or 0
            output_tokens = response.usage.completion_tokens or 0

        model_result = session.execute(
            select(Model).where(Model.id == model_name, Model.is_delete == 0)
        )
        model_row = model_result.scalar_one_or_none()
        if model_row and model_row.input_price is not None and model_row.output_price is not None:
            cost = CostCalculator.calculate_cost(
                model_name,
                input_tokens,
                output_tokens,
                model_row.input_price,
                model_row.output_price
            )
        else:
            cost = (Decimal(str(input_tokens)) / Decimal(str(TOKENS_PER_MILLION))) * DEFAULT_INPUT_PRICE + \
                   (Decimal(str(output_tokens)) / Decimal(str(TOKENS_PER_MILLION))) * DEFAULT_OUTPUT_PRICE
            cost = cost.quantize(Decimal("0.000001"))

        test_result = TestResult(
            id=result_id,
            task_id=task_id,
            user_id=user_id,
            scene_id=sub_task_data.get("sceneId", ""),
            prompt_id=sub_task_data.get("promptId", ""),
            model_name=model_name,
            input_prompt=prompt_content,
            output_text=output_text or "",
            reasoning=None,
            response_time_ms=response_time_ms,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cost=cost,
            is_delete=0
        )
        session.add(test_result)

        session.execute(
            text("""
                UPDATE test_task
                SET completedSubtasks = completedSubtasks + 1,
                    status = CASE
                        WHEN completedSubtasks + 1 >= totalSubtasks THEN 'completed'
                        WHEN status = 'pending' THEN 'running'
                        ELSE status
                    END,
                    startedAt = CASE WHEN startedAt IS NULL THEN NOW() ELSE startedAt END,
                    completedAt = CASE
                        WHEN completedSubtasks + 1 >= totalSubtasks THEN NOW()
                        ELSE completedAt
                    END
                WHERE id = :task_id AND isDelete = 0
            """),
            {"task_id": task_id}
        )

        session.commit()

        from app.services.progress_service import publish_progress
        task_result2 = session.execute(
            select(TestTask).where(TestTask.id == task_id)
        )
        task_after = task_result2.scalar_one_or_none()
        if task_after:
            percentage = int((task_after.completed_subtasks / task_after.total_subtasks * 100)) if task_after.total_subtasks else 0
            publish_progress(task_id, {
                "taskId": task_id,
                "percentage": percentage,
                "completedSubtasks": task_after.completed_subtasks,
                "totalSubtasks": task_after.total_subtasks,
                "currentModel": model_name,
                "currentPrompt": prompt_title,
                "status": task_after.status,
                "timestamp": int(time.time() * 1000)
            })

        return {
            "resultId": result_id,
            "taskId": task_id,
            "modelName": model_name,
            "responseTimeMs": response_time_ms
        }

    except Exception as e:
        session.rollback()
        if task_id:
            try:
                session.execute(
                    text("""
                        UPDATE test_task
                        SET completedSubtasks = completedSubtasks + 1,
                            status = 'failed'
                        WHERE id = :task_id AND isDelete = 0
                    """),
                    {"task_id": task_id}
                )
                session.commit()
                from app.services.progress_service import publish_progress
                publish_progress(task_id, {
                    "taskId": task_id,
                    "status": "failed",
                    "timestamp": int(time.time() * 1000)
                })
            except Exception:
                session.rollback()
        from app.core.logging_config import logger
        logger.exception("子任务执行失败: taskId=%s, model=%s", task_id, model_name)
        raise

    finally:
        session.close()


@celery_app.task(bind=True, max_retries=3)
def process_subtask(self, sub_task_data: dict) -> dict:
    """Celery 任务包装（可选，进程内执行时使用 run_subtask_sync）"""
    try:
        return _do_process_subtask(sub_task_data)
    except Exception as e:
        raise self.retry(exc=e, countdown=5)
