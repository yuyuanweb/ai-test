"""
提示词优化服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import re
from decimal import Decimal
from typing import List, Optional, Any

from openai import AsyncOpenAI
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.errors import BusinessException, ErrorCode
from app.utils.ai_retry_helper import run_with_retry_async
from app.utils.cost_calculator import CostCalculator
from app.utils.model_pricing_cache import get_model_pricing_cached_async
from app.core.logging_config import logger
from app.models.model import Model
from app.schemas.prompt import PromptOptimizationVO
from app.services.budget_service import add_cost_async
from app.services.user_model_usage_service import update_user_model_usage

DEFAULT_EVALUATION_MODEL = "qwen/qwen-plus"

OPTIMIZATION_PROMPT_TEMPLATE = """
你是一位专业的提示词工程专家。请分析以下提示词，并提供优化建议。

## 原始提示词
{original_prompt}

{ai_response_section}

## 分析维度
请从以下5个维度分析提示词：
1. **角色设定**：是否明确指定了AI的角色和身份？
2. **任务描述**：任务目标是否清晰、具体？
3. **输出格式**：是否明确指定了期望的输出格式？
4. **思维链**：是否引导AI进行逐步思考？
5. **Few-shot示例**：是否提供了示例来帮助AI理解需求？

## 输出要求
请以JSON格式输出分析结果：
{{
  "issues": ["问题1", "问题2", ...],
  "optimized_prompt": "优化后的完整提示词",
  "improvements": ["改进点1", "改进点2", ...]
}}

要求：
- issues: 列出当前提示词存在的问题（至少3个维度的问题）
- optimized_prompt: 提供优化后的完整提示词，保持原意但更加清晰、具体
- improvements: 说明每个优化带来的具体提升（至少3个改进点）
"""


def _extract_json_from_response(text: str) -> Optional[str]:
    """
    从模型返回中提取 JSON 字符串（可能被 ```json ... ``` 包裹）
    """
    if not text or not text.strip():
        return None
    text = text.strip()
    match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if match:
        return match.group(1).strip()
    return text


def _parse_optimization_suggestion(raw: str) -> tuple[List[str], str, List[str]]:
    """
    解析优化建议 JSON，返回 (issues, optimized_prompt, improvements)
    """
    json_str = _extract_json_from_response(raw)
    if not json_str:
        return [], "", []

    try:
        data = json.loads(json_str)
        issues = data.get("issues")
        if not isinstance(issues, list):
            issues = []
        optimized_prompt = data.get("optimized_prompt") or ""
        improvements = data.get("improvements")
        if not isinstance(improvements, list):
            improvements = []
        return issues, optimized_prompt, improvements
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("解析提示词优化 JSON 失败: %s", e)
        return [], "", []


class PromptOptimizationService:
    """
    提示词优化服务类
    """

    def __init__(self) -> None:
        settings = get_settings()
        self._client = AsyncOpenAI(
            api_key=settings.OPENROUTER_API_KEY,
            base_url="https://openrouter.ai/api/v1",
        )
        self._extra_headers = {
            "HTTP-Referer": "https://codefather.cn",
            "X-Title": "AI Evaluation Platform",
        }

    async def optimize_prompt(
        self,
        original_prompt: str,
        ai_response: Optional[str] = None,
        evaluation_model: Optional[str] = None,
        user_id: Optional[int] = None,
        db: Optional[AsyncSession] = None,
        redis_client: Any = None,
    ) -> PromptOptimizationVO:
        """
        分析并优化提示词

        Args:
            original_prompt: 原始提示词
            ai_response: AI 回答（可选，用于更精准的分析）
            evaluation_model: 评估模型（可选）
            user_id: 用户 ID（预留，用于统计）

        Returns:
            优化建议 VO

        Raises:
            BusinessException: 业务异常
        """
        if not original_prompt or not original_prompt.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "原始提示词不能为空")

        model = (evaluation_model or "").strip() or DEFAULT_EVALUATION_MODEL
        ai_response_section = ""
        if ai_response and ai_response.strip():
            ai_response_section = "\n## AI回答\n" + ai_response.strip() + "\n"

        analysis_prompt = OPTIMIZATION_PROMPT_TEMPLATE.replace(
            "{original_prompt}", original_prompt.strip()
        ).replace("{ai_response_section}", ai_response_section)

        logger.info(
            "开始提示词优化分析: promptLength=%s, hasResponse=%s, model=%s, userId=%s",
            len(original_prompt),
            bool(ai_response and ai_response.strip()),
            model,
            user_id,
        )

        try:
            resp = await run_with_retry_async(
                lambda: self._client.chat.completions.create(
                    model=model,
                    messages=[{"role": "user", "content": analysis_prompt}],
                    temperature=0.3,
                    max_tokens=2048,
                    extra_headers=self._extra_headers,
                )
            )
            content = ""
            if resp.choices and len(resp.choices) > 0:
                content = (resp.choices[0].message.content or "").strip()

            issues, optimized_prompt, improvements = _parse_optimization_suggestion(content)

            prompt_tokens = (resp.usage.prompt_tokens or 0) if resp.usage else 0
            completion_tokens = (resp.usage.completion_tokens or 0) if resp.usage else 0
            total_tokens = prompt_tokens + completion_tokens
            cost = Decimal("0")
            if total_tokens > 0 and user_id and db and redis_client:
                async def _fetch_pricing():
                    from sqlalchemy import select
                    r = await db.execute(
                        select(Model).where(Model.id == model, Model.is_delete == 0)
                    )
                    m = r.scalar_one_or_none()
                    if m:
                        return (m.input_price, m.output_price)
                    return (None, None)
                input_price, output_price = await get_model_pricing_cached_async(
                    redis_client, model, _fetch_pricing
                )
                if input_price is not None and output_price is not None:
                    cost = CostCalculator.calculate_cost(
                        model, prompt_tokens, completion_tokens, input_price, output_price
                    )
                else:
                    cost = (
                        (Decimal(str(prompt_tokens)) / Decimal("1000000")) * Decimal("1")
                        + (Decimal(str(completion_tokens)) / Decimal("1000000")) * Decimal("2")
                    ).quantize(Decimal("0.000001"))
                if cost and cost > 0:
                    try:
                        await add_cost_async(redis_client, user_id, cost)
                        await update_user_model_usage(db, user_id, model, total_tokens, cost)
                    except Exception as e:
                        logger.warning("提示词优化成本追踪失败: userId=%s, error=%s", user_id, str(e))
            if user_id and total_tokens > 0:
                logger.info(
                    "提示词优化统计: userId=%s, model=%s, tokens=%s, cost=%s",
                    user_id,
                    model,
                    total_tokens,
                    cost,
                )

            logger.info(
                "提示词优化分析完成: issuesCount=%s, improvementsCount=%s, tokens=%s",
                len(issues),
                len(improvements),
                total_tokens,
            )

            return PromptOptimizationVO(
                issues=issues,
                optimizedPrompt=optimized_prompt,
                improvements=improvements,
            )
        except Exception as e:
            logger.error("提示词优化分析失败: prompt=%s, error=%s", original_prompt[:100], e, exc_info=True)
            raise BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "提示词优化分析失败: " + (str(e) if e else "未知错误"),
            )
