"""
批量测试子任务执行器（进程内执行，无需 Celery Worker）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import uuid
import time
from decimal import Decimal

from sqlalchemy import select, text
from openai import OpenAI

from app.core.config import get_settings
from app.db.redis import get_redis_client_sync
from app.db.sync_session import get_sync_session
from app.models.test_task import TestTask
from app.models.test_result import TestResult
from app.models.model import Model
from app.utils.cost_calculator import CostCalculator
from app.utils.model_pricing_cache import get_model_pricing_cached_sync
from app.utils.prompt_guardrail import validate as validate_prompt

settings = get_settings()
DEFAULT_TEMPERATURE = 0.7
DEFAULT_INPUT_PRICE = Decimal("1")
DEFAULT_OUTPUT_PRICE = Decimal("2")
TOKENS_PER_MILLION = 1_000_000

OPENROUTER_EXTRA_HEADERS = {
    "HTTP-Referer": "https://codefather.cn",
    "X-Title": "AI Evaluation Platform",
}


def _check_enable_ai_scoring(config: dict) -> bool:
    """
    检查任务是否启用 AI 评分（与 Java checkEnableAiScoring 一致）
    """
    enable = config.get("enableAiScoring")
    if isinstance(enable, bool):
        return enable
    if isinstance(enable, str):
        return enable.lower() in ("true", "1", "yes")
    return False


def run_subtask_sync(sub_task_data: dict) -> dict:
    """
    同步执行单个子任务（在线程池中运行，进程内执行，无需 Celery Worker）
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

        validate_prompt(prompt_content)

        config = {}
        if task.config:
            try:
                config = json.loads(task.config)
            except json.JSONDecodeError:
                pass

        temperature = config.get("temperature", DEFAULT_TEMPERATURE)
        if not isinstance(temperature, (int, float)):
            temperature = DEFAULT_TEMPERATURE
        max_tokens = config.get("maxTokens", 4096)
        if not isinstance(max_tokens, (int, float)):
            max_tokens = 4096
        max_tokens = int(max_tokens)

        client = OpenAI(
            api_key=settings.OPENROUTER_API_KEY,
            base_url="https://openrouter.ai/api/v1"
        )

        SUBTASK_TIMEOUT_SECONDS = 30
        response = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": prompt_content}],
            temperature=float(temperature),
            max_tokens=max_tokens,
            timeout=SUBTASK_TIMEOUT_SECONDS,
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

        def _fetch_pricing():
            model_result = session.execute(
                select(Model).where(Model.id == model_name, Model.is_delete == 0)
            )
            model_row = model_result.scalar_one_or_none()
            if model_row:
                return (model_row.input_price, model_row.output_price)
            return (None, None)

        input_price, output_price = (None, None)
        try:
            redis_client = get_redis_client_sync()
            if redis_client:
                input_price, output_price = get_model_pricing_cached_sync(
                    redis_client, model_name, _fetch_pricing
                )
        except Exception:
            pass
        if input_price is None and output_price is None:
            input_price, output_price = _fetch_pricing()

        if input_price is not None and output_price is not None:
            cost = CostCalculator.calculate_cost(
                model_name, input_tokens, output_tokens, input_price, output_price
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

        enable_ai_scoring = _check_enable_ai_scoring(config)
        if enable_ai_scoring and output_text:
            from app.services.ai_scoring_service import run_ai_scoring_sync
            from app.schemas.evaluation import ai_score_result_to_json
            ai_result = run_ai_scoring_sync(
                sync_session=session,
                openai_sync_client=client,
                question=prompt_content,
                model_response=output_text,
                tested_model_name=model_name,
                extra_headers=OPENROUTER_EXTRA_HEADERS,
            )
            if ai_result is not None:
                test_result.ai_score = ai_score_result_to_json(ai_result)

        total_tokens = input_tokens + output_tokens
        session.execute(
            text("""
                UPDATE model
                SET totalTokens = totalTokens + :tokens,
                    totalCost = totalCost + :cost
                WHERE id = :model_id AND isDelete = 0
            """),
            {"tokens": total_tokens, "cost": float(cost), "model_id": model_name}
        )

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
            completed = task_after.completed_subtasks
            total = task_after.total_subtasks
            should_push = (
                completed >= total or
                completed % 10 == 0 or
                task_after.status in ("completed", "failed")
            )
            if should_push:
                percentage = int((completed / total * 100)) if total else 0
                publish_progress(task_id, {
                    "taskId": task_id,
                    "percentage": percentage,
                    "completedSubtasks": completed,
                    "totalSubtasks": total,
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
        logger.exception("子任务执行失败: taskId={}, model={}", task_id, model_name)
        raise

    finally:
        session.close()
