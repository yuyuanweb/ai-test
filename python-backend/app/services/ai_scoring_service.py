"""
AI 评分服务接口与实现
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import json
import re
from abc import ABC, abstractmethod
from decimal import Decimal
from typing import List, Optional, Any

from openai import AsyncOpenAI, OpenAI
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import BusinessException, ErrorCode
from app.utils.ai_retry_helper import run_with_retry, run_with_retry_async
from app.utils.cost_calculator import CostCalculator
from app.utils.model_pricing_cache import get_model_pricing_cached_sync
from app.core.logging_config import logger
from app.models.model import Model
from app.schemas.evaluation import EvaluationResult, JudgeScore, AIScoreResult

SCORING_PROMPT_TEMPLATE = """
你是一位专业的AI评测专家。请对以下AI模型的回答进行评分。

## 问题
{question}

## 模型回答
{model_response}

## 评分标准
1. 准确性（30分）：答案是否正确，事实是否准确
2. 相关性（20分）：是否切题，是否回答了问题
3. 完整性（20分）：是否全面，是否遗漏重要信息
4. 清晰度（15分）：表达是否清楚，逻辑是否连贯
5. 创意性（15分）：是否有独特见解或创新点

请以JSON格式输出评分结果：
{{
  "scores": {{
    "accuracy": 分数,
    "relevance": 分数,
    "completeness": 分数,
    "clarity": 分数,
    "creativity": 分数
  }},
  "total_score": 总分（100分制）,
  "rating": 评级（1-10分）,
  "comment": "简短评价（50字以内）"
}}
"""


def build_scoring_prompt(question: str, model_response: str) -> str:
    """
    根据问题和模型回答构建评分提示词

    Args:
        question: 用户问题
        model_response: 被评测模型的回答

    Returns:
        填充后的评分提示词
    """
    return SCORING_PROMPT_TEMPLATE.replace("{question}", question).replace(
        "{model_response}", model_response
    )


class AIScoringService(ABC):
    """
    AI 评分服务接口
    与 Java AIScoringService 对齐
    """

    @abstractmethod
    async def score(
        self,
        question: str,
        model_response: str,
        user_id: Optional[int] = None,
    ) -> EvaluationResult:
        """
        对模型回答进行 AI 评分（单评委）

        Args:
            question: 问题
            model_response: 模型回答
            user_id: 用户 ID（用于统计模型使用量）

        Returns:
            评分结果
        """
        ...

    @abstractmethod
    async def score_with_multiple_judges(
        self,
        question: str,
        model_response: str,
        tested_model_name: str,
        user_id: Optional[int] = None,
    ) -> AIScoreResult:
        """
        对模型回答进行多评委交叉验证评分

        Args:
            question: 问题
            model_response: 模型回答
            tested_model_name: 被测试的模型名称（用于排除，避免自己评自己）
            user_id: 用户 ID（用于统计模型使用量）

        Returns:
            多评委评分结果
        """
        ...


JUDGE_MODEL_DEFAULT = "qwen/qwen-plus"
MAX_JUDGES = 3
MIN_JUDGES = 2
SCORING_RETRY_TIMES = 3


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


def _parse_evaluation_result(raw: str) -> Optional[EvaluationResult]:
    """
    解析评委模型返回的文本为 EvaluationResult
    """
    json_str = _extract_json_from_response(raw)
    if not json_str:
        return None
    try:
        data = json.loads(json_str)
        return EvaluationResult(
            scores=data.get("scores") or {},
            total_score=int(data.get("total_score", 0)),
            rating=int(data.get("rating", 0)),
            comment=str(data.get("comment") or ""),
        )
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("解析评分 JSON 失败: %s", e)
        return None


def _is_same_provider(model_id1: Optional[str], model_id2: Optional[str]) -> bool:
    """
    判断两个模型是否来自同一提供商（避免同一提供商不同模型互相评分）
    """
    if not model_id1 or not model_id2:
        return False
    p1 = model_id1.split("/")[0] if "/" in model_id1 else ""
    p2 = model_id2.split("/")[0] if "/" in model_id2 else ""
    return bool(p1 and p2 and p1 == p2)


def _calculate_average_rating(judge_scores: List[JudgeScore]) -> float:
    """计算平均评级"""
    if not judge_scores:
        return 0.0
    total = sum(js.rating for js in judge_scores if js.rating is not None)
    return total / len(judge_scores)


def _calculate_consistency(judge_scores: List[JudgeScore]) -> float:
    """计算评分一致性（标准差）"""
    if len(judge_scores) < 2:
        return 0.0
    avg = _calculate_average_rating(judge_scores)
    variance = sum(
        (js.rating - avg) ** 2 for js in judge_scores if js.rating is not None
    ) / len(judge_scores)
    return variance ** 0.5


async def _select_judge_models(
    db: AsyncSession, tested_model_name: Optional[str]
) -> List[str]:
    """
    选择评委模型：国内模型、排除被测模型及同提供商，按推荐与更新时间排序，取 2~3 个
    """
    stmt = (
        select(Model.id)
        .where(Model.is_delete == 0, Model.is_china == 1)
        .order_by(Model.recommended.desc(), Model.update_time.desc())
    )
    result = await db.execute(stmt)
    ids = [row[0] for row in result.fetchall()]
    candidates = [
        mid
        for mid in ids
        if mid != tested_model_name and not _is_same_provider(mid, tested_model_name)
    ]
    count = min(MAX_JUDGES, max(MIN_JUDGES, len(candidates)))
    return candidates[:count]


class AIScoringServiceImpl(AIScoringService):
    """
    AI 评分服务实现：单评委与多评委交叉验证
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

    async def _invoke_judge(
        self, prompt: str, model_name: str
    ) -> tuple[Optional[EvaluationResult], int, int]:
        """
        调用单个评委模型，返回 (解析结果, input_tokens, output_tokens)
        """
        try:
            resp = await run_with_retry_async(
                lambda: self._client.chat.completions.create(
                    model=model_name,
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0.3,
                    max_tokens=1024,
                    extra_headers=self._extra_headers,
                )
            )
            content = ""
            if resp.choices and len(resp.choices) > 0:
                content = (resp.choices[0].message.content or "").strip()
            input_tokens = resp.usage.prompt_tokens if resp.usage else 0
            output_tokens = resp.usage.completion_tokens if resp.usage else 0
            ev = _parse_evaluation_result(content)
            return ev, input_tokens, output_tokens
        except Exception as e:
            logger.error("评委 %s 评分失败: %s", model_name, e)
            return None, 0, 0

    async def score(
        self,
        question: str,
        model_response: str,
        user_id: Optional[int] = None,
        db: Optional[AsyncSession] = None,
        redis_client: Any = None,
    ) -> EvaluationResult:
        if not question or not question.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空")
        if not model_response or not model_response.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型回答不能为空")

        prompt = build_scoring_prompt(question.strip(), model_response.strip())
        logger.info(
            "开始AI评分: questionLength=%s, responseLength=%s, userId=%s",
            len(question),
            len(model_response),
            user_id,
        )
        ev, inp_tok, out_tok = await self._invoke_judge(prompt, JUDGE_MODEL_DEFAULT)
        if ev is None:
            raise BusinessException(
                ErrorCode.SYSTEM_ERROR, "AI评分失败: 评委返回无法解析"
            )
        if user_id and db and redis_client and (inp_tok + out_tok) > 0:
            await self._record_judge_cost_async(
                db, redis_client, user_id, JUDGE_MODEL_DEFAULT, inp_tok, out_tok
            )
        logger.info("AI评分完成: totalScore=%s, rating=%s", ev.total_score, ev.rating)
        return ev

    async def _record_judge_cost_async(
        self,
        db: AsyncSession,
        redis_client: Any,
        user_id: int,
        model_name: str,
        inp_tok: int,
        out_tok: int,
    ) -> None:
        """记录评委模型成本（异步）"""
        try:
            from app.utils.model_pricing_cache import get_model_pricing_cached_async
            from app.services.budget_service import add_cost_async
            from app.services.user_model_usage_service import update_user_model_usage

            async def _fetch():
                r = await db.execute(
                    select(Model).where(Model.id == model_name, Model.is_delete == 0)
                )
                m = r.scalar_one_or_none()
                if m:
                    return (m.input_price, m.output_price)
                return (None, None)
            inp_p, out_p = await get_model_pricing_cached_async(
                redis_client, model_name, _fetch
            )
            if inp_p is None and out_p is None:
                inp_p, out_p = await _fetch()
            cost = Decimal("0")
            if inp_p is not None and out_p is not None:
                cost = CostCalculator.calculate_cost(
                    model_name, inp_tok, out_tok, inp_p, out_p
                )
            else:
                cost = (
                    (Decimal(str(inp_tok)) / Decimal("1000000")) * Decimal("1")
                    + (Decimal(str(out_tok)) / Decimal("1000000")) * Decimal("2")
                ).quantize(Decimal("0.000001"))
            if cost and cost > 0:
                await add_cost_async(redis_client, user_id, cost)
                await update_user_model_usage(
                    db, user_id, model_name, inp_tok + out_tok, cost
                )
        except Exception as e:
            logger.warning("AI评分成本追踪失败: userId=%s, error=%s", user_id, str(e))

    async def score_with_multiple_judges(
        self,
        question: str,
        model_response: str,
        tested_model_name: str,
        user_id: Optional[int] = None,
        db: Optional[AsyncSession] = None,
        redis_client: Any = None,
    ) -> AIScoreResult:
        if not question or not question.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空")
        if not model_response or not model_response.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型回答不能为空")

        question = question.strip()
        model_response = model_response.strip()
        prompt = build_scoring_prompt(question, model_response)

        judge_models: List[str] = []
        if db is not None:
            judge_models = await _select_judge_models(db, tested_model_name)

        if not judge_models:
            logger.warning(
                "未找到可用的评委模型，使用单评委模式: testedModel=%s",
                tested_model_name,
            )
            single = await self.score(
                question, model_response, user_id, db=db, redis_client=redis_client
            )
            js = JudgeScore(
                model=JUDGE_MODEL_DEFAULT,
                scores=single.scores,
                total_score=single.total_score,
                rating=single.rating,
                comment=single.comment,
            )
            avg = float(single.rating) if single.rating is not None else 0.0
            return AIScoreResult(
                judges=[js],
                average_rating=avg,
                consistency=0.0,
            )

        logger.info(
            "开始多评委交叉验证评分: testedModel=%s, judges=%s, questionLength=%s, responseLength=%s, userId=%s",
            tested_model_name,
            judge_models,
            len(question),
            len(model_response),
            user_id,
        )

        judge_usage_list: List[tuple[str, int, int]] = []

        async def one_judge(model_name: str) -> Optional[JudgeScore]:
            ev, inp_tok, out_tok = await self._invoke_judge(prompt, model_name)
            if ev is None:
                return None
            judge_usage_list.append((model_name, inp_tok, out_tok))
            logger.info(
                "评委 %s 评分完成: totalScore=%s, rating=%s",
                model_name,
                ev.total_score,
                ev.rating,
            )
            return JudgeScore(
                model=model_name,
                scores=ev.scores,
                total_score=ev.total_score,
                rating=ev.rating,
                comment=ev.comment,
            )

        tasks = [one_judge(m) for m in judge_models]
        results = await asyncio.gather(*tasks)
        judge_scores = [r for r in results if r is not None]

        if not judge_scores:
            raise BusinessException(
                ErrorCode.SYSTEM_ERROR, "所有评委评分都失败了"
            )

        average_rating = _calculate_average_rating(judge_scores)
        consistency = _calculate_consistency(judge_scores)
        if user_id and db and redis_client:
            for model_name, inp_tok, out_tok in judge_usage_list:
                if (inp_tok + out_tok) > 0:
                    await self._record_judge_cost_async(
                        db, redis_client, user_id, model_name, inp_tok, out_tok
                    )
        logger.info(
            "多评委评分完成: testedModel=%s, judges=%s, averageRating=%s, consistency=%s",
            tested_model_name,
            len(judge_scores),
            average_rating,
            consistency,
        )
        return AIScoreResult(
            judges=judge_scores,
            average_rating=average_rating,
            consistency=consistency,
        )


def _select_judge_models_sync(
    sync_session: Session, tested_model_name: Optional[str]
) -> List[str]:
    """
    同步：选择评委模型（与 _select_judge_models 逻辑一致）
    """
    stmt = (
        select(Model.id)
        .where(Model.is_delete == 0, Model.is_china == 1)
        .order_by(Model.recommended.desc(), Model.update_time.desc())
    )
    result = sync_session.execute(stmt)
    ids = [row[0] for row in result.fetchall()]
    candidates = [
        mid
        for mid in ids
        if mid != tested_model_name and not _is_same_provider(mid, tested_model_name)
    ]
    count = min(MAX_JUDGES, max(MIN_JUDGES, len(candidates)))
    return candidates[:count]


def _invoke_judge_sync(
    client: OpenAI,
    prompt: str,
    model_name: str,
    extra_headers: Optional[dict] = None,
) -> tuple[Optional[EvaluationResult], int, int]:
    """
    同步调用单个评委模型，返回 (解析结果, input_tokens, output_tokens)
    """
    try:
        resp = run_with_retry(
            lambda: client.chat.completions.create(
                model=model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.3,
                max_tokens=1024,
                extra_headers=extra_headers or {},
            )
        )
        content = ""
        if resp.choices and len(resp.choices) > 0:
            content = (resp.choices[0].message.content or "").strip()
        input_tokens = resp.usage.prompt_tokens if resp.usage else 0
        output_tokens = resp.usage.completion_tokens if resp.usage else 0
        return _parse_evaluation_result(content), input_tokens, output_tokens
    except Exception as e:
        logger.error("评委 %s 同步评分失败: %s", model_name, e)
        return None, 0, 0


def run_ai_scoring_sync(
    sync_session: Session,
    openai_sync_client: OpenAI,
    question: str,
    model_response: str,
    tested_model_name: str,
    extra_headers: Optional[dict] = None,
    user_id: Optional[int] = None,
    redis_client: Any = None,
) -> Optional[AIScoreResult]:
    """
    同步执行多评委 AI 评分，供批量测试 worker 在子线程中调用，避免 asyncio 与多线程冲突。
    失败时返回 None，不抛异常。
    """
    if not question or not question.strip() or not model_response or not model_response.strip():
        return None
    question = question.strip()
    model_response = model_response.strip()
    prompt = build_scoring_prompt(question, model_response)
    headers = extra_headers or {
        "HTTP-Referer": "https://codefather.cn",
        "X-Title": "AI Evaluation Platform",
    }

    judge_models: List[str] = []
    try:
        judge_models = _select_judge_models_sync(sync_session, tested_model_name)
    except Exception as e:
        logger.warning("同步选择评委模型失败: %s", e)

    def _calc_judge_cost(model_id: str, inp: int, out: int) -> Decimal:
        def _fetch():
            r = sync_session.execute(
                select(Model).where(Model.id == model_id, Model.is_delete == 0)
            )
            m = r.scalar_one_or_none()
            if m:
                return (m.input_price, m.output_price)
            return (None, None)
        inp_p, out_p = (None, None)
        if redis_client:
            try:
                inp_p, out_p = get_model_pricing_cached_sync(redis_client, model_id, _fetch)
            except Exception:
                pass
        if inp_p is None and out_p is None:
            inp_p, out_p = _fetch()
        if inp_p is not None and out_p is not None:
            return CostCalculator.calculate_cost(model_id, inp, out, inp_p, out_p)
        return (
            (Decimal(str(inp)) / Decimal("1000000")) * Decimal("1")
            + (Decimal(str(out)) / Decimal("1000000")) * Decimal("2")
        ).quantize(Decimal("0.000001"))

    if not judge_models:
        ev, inp_tok, out_tok = _invoke_judge_sync(
            openai_sync_client, prompt, JUDGE_MODEL_DEFAULT, headers
        )
        if ev is None:
            return None
        if user_id and redis_client and (inp_tok + out_tok) > 0:
            try:
                from app.services.budget_service import add_cost_sync
                from app.services.user_model_usage_service import update_user_model_usage_sync
                cost = _calc_judge_cost(JUDGE_MODEL_DEFAULT, inp_tok, out_tok)
                if cost and cost > 0:
                    add_cost_sync(redis_client, user_id, cost)
                    update_user_model_usage_sync(
                        sync_session, user_id, JUDGE_MODEL_DEFAULT,
                        inp_tok + out_tok, cost
                    )
            except Exception as e:
                logger.warning("AI评分成本追踪失败: userId=%s, error=%s", user_id, str(e))
        js = JudgeScore(
            model=JUDGE_MODEL_DEFAULT,
            scores=ev.scores,
            total_score=ev.total_score,
            rating=ev.rating,
            comment=ev.comment,
        )
        avg = float(ev.rating) if ev.rating is not None else 0.0
        return AIScoreResult(judges=[js], average_rating=avg, consistency=0.0)

    judge_scores: List[JudgeScore] = []
    judge_usage: List[tuple[str, int, int]] = []
    for model_name in judge_models:
        ev, inp_tok, out_tok = _invoke_judge_sync(
            openai_sync_client, prompt, model_name, headers
        )
        if ev is not None:
            judge_scores.append(
                JudgeScore(
                    model=model_name,
                    scores=ev.scores,
                    total_score=ev.total_score,
                    rating=ev.rating,
                    comment=ev.comment,
                )
            )
            judge_usage.append((model_name, inp_tok, out_tok))

    if not judge_scores:
        return None
    if user_id and redis_client:
        try:
            from app.services.budget_service import add_cost_sync
            from app.services.user_model_usage_service import update_user_model_usage_sync
            for model_name, inp_tok, out_tok in judge_usage:
                if (inp_tok + out_tok) > 0:
                    cost = _calc_judge_cost(model_name, inp_tok, out_tok)
                    if cost and cost > 0:
                        add_cost_sync(redis_client, user_id, cost)
                        update_user_model_usage_sync(
                            sync_session, user_id, model_name,
                            inp_tok + out_tok, cost
                        )
        except Exception as e:
            logger.warning("AI评分成本追踪失败: userId=%s, error=%s", user_id, str(e))
    average_rating = _calculate_average_rating(judge_scores)
    consistency = _calculate_consistency(judge_scores)
    return AIScoreResult(
        judges=judge_scores,
        average_rating=average_rating,
        consistency=consistency,
    )
