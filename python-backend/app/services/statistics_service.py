"""
统计服务
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
from datetime import date, datetime, timedelta
from decimal import Decimal
from collections import defaultdict
from typing import Any, List, Optional

from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.constants import CacheConstant
from app.core.logging_config import logger
from app.models.user import User
from app.models.conversation_message import ConversationMessage
from app.models.test_result import TestResult
from app.models.user_model_usage import UserModelUsage
from app.schemas.statistics import (
    CostStatisticsVO, ModelCostVO, DailyCostVO,
    UsageStatisticsVO, ModelUsageVO, DailyUsageVO,
    PerformanceStatisticsVO, ModelPerformanceVO,
    RealtimeCostVO
)
from app.services.budget_service import (
    get_today_cost_with_sync,
    get_month_cost_with_sync,
    check_budget
)

DATE_FORMAT = "%Y-%m-%d"
DEFAULT_DAYS = 30
DEFAULT_ALERT_THRESHOLD = 80


def _serialize_for_cache(obj: Any) -> str:
    return json.dumps(obj, default=str, ensure_ascii=False)


def _days_ago(n: int) -> datetime:
    return datetime.combine(date.today() - timedelta(days=n), datetime.min.time())


async def get_realtime_cost(
    db: AsyncSession,
    redis_client: Any,
    user_id: int
) -> RealtimeCostVO:
    """获取实时成本监控数据"""
    result = await db.execute(select(User).where(User.id == user_id, User.is_delete == 0))
    user = result.scalar_one_or_none()
    if user is None:
        return RealtimeCostVO(
            budget_status="normal",
            budget_message="预算充足"
        )
    daily_budget = user.daily_budget
    monthly_budget = user.monthly_budget
    alert_threshold = user.budget_alert_threshold if user.budget_alert_threshold is not None else DEFAULT_ALERT_THRESHOLD
    today_cost = await get_today_cost_with_sync(db, redis_client, user_id)
    month_cost = await get_month_cost_with_sync(db, redis_client, user_id)
    today_start = datetime.combine(date.today(), datetime.min.time())
    cm_today_calls = await db.execute(
        select(func.count()).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.role == "assistant",
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= today_start
        )
    )
    tr_today_count = await db.execute(
        select(func.count()).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= today_start
        )
    )
    today_api_calls = (cm_today_calls.scalar() or 0) + (tr_today_count.scalar() or 0)
    cm_today_tokens = await db.execute(
        select(
            func.coalesce(func.sum(
                func.coalesce(ConversationMessage.input_tokens, 0) + func.coalesce(ConversationMessage.output_tokens, 0)
            ), 0)
        ).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= today_start
        )
    )
    tr_today_tokens = await db.execute(
        select(
            func.coalesce(func.sum(
                func.coalesce(TestResult.input_tokens, 0) + func.coalesce(TestResult.output_tokens, 0)
            ), 0)
        ).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= today_start
        )
    )
    today_tokens = int(cm_today_tokens.scalar() or 0) + int(tr_today_tokens.scalar() or 0)
    avg_cost_per_call = Decimal("0")
    if today_api_calls and today_api_calls > 0 and today_cost:
        avg_cost_per_call = (today_cost / today_api_calls).quantize(Decimal("0.000001"))
    daily_usage_percent = Decimal("0")
    if daily_budget and daily_budget > 0:
        daily_usage_percent = (today_cost * 100 / daily_budget).quantize(Decimal("0.01"))
    monthly_usage_percent = Decimal("0")
    if monthly_budget and monthly_budget > 0:
        monthly_usage_percent = (month_cost * 100 / monthly_budget).quantize(Decimal("0.01"))
    budget_status_vo = await check_budget(db, redis_client, user_id)
    return RealtimeCostVO(
        today_cost=today_cost,
        month_cost=month_cost,
        today_tokens=today_tokens,
        today_api_calls=today_api_calls or 0,
        avg_cost_per_call=avg_cost_per_call,
        daily_budget=daily_budget,
        monthly_budget=monthly_budget,
        daily_usage_percent=daily_usage_percent,
        monthly_usage_percent=monthly_usage_percent,
        budget_status=budget_status_vo.status,
        budget_message=budget_status_vo.message or "预算充足",
        alert_threshold=alert_threshold
    )


async def get_cost_statistics(
    db: AsyncSession,
    redis_client: Any,
    user_id: int,
    days: int = DEFAULT_DAYS
) -> CostStatisticsVO:
    """获取成本统计数据"""
    if days <= 0:
        days = DEFAULT_DAYS
    cache_key = f"{CacheConstant.STATISTICS_COST_KEY_PREFIX}{user_id}:{days}"
    if redis_client:
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                data = json.loads(cached)
                return CostStatisticsVO.model_validate(data)
        except Exception as e:
            logger.warning("读取成本统计缓存失败: %s", str(e))
    usage_result = await db.execute(
        select(UserModelUsage).where(
            UserModelUsage.user_id == user_id,
            UserModelUsage.is_delete == 0
        )
    )
    usage_list = usage_result.scalars().all()
    total_cost = sum(
        (u.total_cost or Decimal("0")) for u in usage_list
    )
    today_cost = await get_today_cost_with_sync(db, redis_client, user_id)
    month_cost = await get_month_cost_with_sync(db, redis_client, user_id)
    first_day = date.today()
    week_start = first_day - timedelta(days=first_day.weekday())
    week_start_dt = datetime.combine(week_start, datetime.min.time())
    cm_week = await db.execute(
        select(func.coalesce(func.sum(ConversationMessage.cost), 0)).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= week_start_dt
        )
    )
    tr_week = await db.execute(
        select(func.coalesce(func.sum(TestResult.cost), 0)).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= week_start_dt
        )
    )
    week_cost = Decimal(str(cm_week.scalar() or 0)) + Decimal(str(tr_week.scalar() or 0))
    model_costs = []
    for u in usage_list:
        if u.model_name and (u.total_cost or Decimal("0")) > 0:
            pct = (u.total_cost * 100 / total_cost).quantize(Decimal("0.01")) if total_cost > 0 else Decimal("0")
            model_costs.append(ModelCostVO(
                model_name=u.model_name,
                cost=u.total_cost or Decimal("0"),
                percentage=pct
            ))
    model_costs.sort(key=lambda x: x.cost, reverse=True)
    start_date = date.today() - timedelta(days=days - 1)
    end_date = date.today()
    cm_trend_result = await db.execute(
        select(ConversationMessage.create_time, ConversationMessage.cost).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= datetime.combine(start_date, datetime.min.time()),
            ConversationMessage.create_time < datetime.combine(end_date, datetime.min.time()) + timedelta(days=1)
        )
    )
    tr_trend_result = await db.execute(
        select(TestResult.create_time, TestResult.cost).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= datetime.combine(start_date, datetime.min.time()),
            TestResult.create_time < datetime.combine(end_date, datetime.min.time()) + timedelta(days=1)
        )
    )
    daily_costs = defaultdict(Decimal)
    for row in cm_trend_result.all():
        ct, cost_val = row[0], row[1]
        if ct and cost_val is not None:
            k = ct.strftime(DATE_FORMAT) if hasattr(ct, "strftime") else str(ct)[:10]
            daily_costs[k] += cost_val
    for row in tr_trend_result.all():
        ct, cost_val = row[0], row[1]
        if ct and cost_val is not None:
            k = ct.strftime(DATE_FORMAT) if hasattr(ct, "strftime") else str(ct)[:10]
            daily_costs[k] += cost_val
    cost_trend = []
    d = start_date
    while d <= end_date:
        k = d.strftime(DATE_FORMAT)
        cost_trend.append(DailyCostVO(date=k, cost=daily_costs.get(k, Decimal("0"))))
        d += timedelta(days=1)
    result = CostStatisticsVO(
        total_cost=total_cost,
        today_cost=today_cost,
        week_cost=week_cost,
        month_cost=month_cost,
        cost_by_model=model_costs,
        cost_trend=cost_trend
    )
    if redis_client:
        try:
            await redis_client.set(
                cache_key,
                _serialize_for_cache(result.model_dump(mode="json")),
                ex=CacheConstant.STATISTICS_TTL_MINUTES * 60
            )
        except Exception as e:
            logger.warning("写入成本统计缓存失败: %s", str(e))
    return result


async def get_usage_statistics(
    db: AsyncSession,
    redis_client: Any,
    user_id: int,
    days: int = DEFAULT_DAYS
) -> UsageStatisticsVO:
    """获取使用量统计数据"""
    if days <= 0:
        days = DEFAULT_DAYS
    cache_key = f"{CacheConstant.STATISTICS_USAGE_KEY_PREFIX}{user_id}:{days}"
    if redis_client:
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                return UsageStatisticsVO.model_validate(json.loads(cached))
        except Exception as e:
            logger.warning("读取使用量统计缓存失败: %s", str(e))
    usage_result = await db.execute(
        select(UserModelUsage).where(
            UserModelUsage.user_id == user_id,
            UserModelUsage.is_delete == 0
        )
    )
    usage_list = usage_result.scalars().all()
    total_tokens = sum(u.total_tokens or 0 for u in usage_list)
    cm_all = await db.execute(
        select(ConversationMessage).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.role == "assistant",
            ConversationMessage.is_delete == 0,
            ConversationMessage.model_name.isnot(None)
        )
    )
    cm_messages = cm_all.scalars().all()
    tr_all = await db.execute(
        select(TestResult).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0
        )
    )
    tr_results = tr_all.scalars().all()
    call_count_by_model = defaultdict(int)
    for m in cm_messages:
        if m.model_name:
            call_count_by_model[m.model_name] += 1
    for _ in tr_results:
        pass
    total_api_calls = len(cm_messages) + len(tr_results)
    today_start = datetime.combine(date.today(), datetime.min.time())
    cm_today = await db.execute(
        select(func.count()).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.role == "assistant",
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= today_start
        )
    )
    tr_today = await db.execute(
        select(func.count()).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= today_start
        )
    )
    today_api_calls = (cm_today.scalar() or 0) + (tr_today.scalar() or 0)
    cm_input = await db.execute(
        select(func.coalesce(func.sum(ConversationMessage.input_tokens), 0)).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0
        )
    )
    cm_output = await db.execute(
        select(func.coalesce(func.sum(ConversationMessage.output_tokens), 0)).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0
        )
    )
    tr_input = await db.execute(
        select(func.coalesce(func.sum(TestResult.input_tokens), 0)).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0
        )
    )
    tr_output = await db.execute(
        select(func.coalesce(func.sum(TestResult.output_tokens), 0)).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0
        )
    )
    total_input = int(cm_input.scalar() or 0) + int(tr_input.scalar() or 0)
    total_output = int(cm_output.scalar() or 0) + int(tr_output.scalar() or 0)
    cm_today_tokens = await db.execute(
        select(
            func.coalesce(func.sum(
                func.coalesce(ConversationMessage.input_tokens, 0) + func.coalesce(ConversationMessage.output_tokens, 0)
            ), 0)
        ).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= today_start
        )
    )
    tr_today_tokens = await db.execute(
        select(
            func.coalesce(func.sum(
                func.coalesce(TestResult.input_tokens, 0) + func.coalesce(TestResult.output_tokens, 0)
            ), 0)
        ).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= today_start
        )
    )
    today_tokens = int(cm_today_tokens.scalar() or 0) + int(tr_today_tokens.scalar() or 0)
    usage_by_model_map = {u.model_name: u for u in usage_list if u.model_name}
    for r in tr_results:
        if r.model_name:
            call_count_by_model[r.model_name] = call_count_by_model.get(r.model_name, 0) + 1
    usage_by_model = []
    for model_name, u in usage_by_model_map.items():
        calls = call_count_by_model.get(model_name, 0)
        pct = (calls * 100 / total_api_calls) if total_api_calls > 0 else 0.0
        usage_by_model.append(ModelUsageVO(
            model_name=model_name,
            call_count=calls,
            tokens=u.total_tokens or 0,
            percentage=round(pct, 2)
        ))
    usage_by_model.sort(key=lambda x: x.call_count, reverse=True)
    start_dt = datetime.combine(date.today() - timedelta(days=days - 1), datetime.min.time())
    end_dt = datetime.combine(date.today(), datetime.min.time()) + timedelta(days=1)
    cm_trend_res = await db.execute(
        select(ConversationMessage.create_time, ConversationMessage.input_tokens, ConversationMessage.output_tokens).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.role == "assistant",
            ConversationMessage.is_delete == 0,
            ConversationMessage.create_time >= start_dt,
            ConversationMessage.create_time < end_dt
        )
    )
    tr_trend_res = await db.execute(
        select(TestResult.create_time, TestResult.input_tokens, TestResult.output_tokens).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.create_time >= start_dt,
            TestResult.create_time < end_dt
        )
    )
    daily_calls = defaultdict(int)
    daily_tokens_map = defaultdict(int)
    for row in cm_trend_res.all():
        ct, inp, out = row[0], row[1], row[2]
        if ct:
            k = ct.strftime(DATE_FORMAT) if hasattr(ct, "strftime") else str(ct)[:10]
            daily_calls[k] += 1
            daily_tokens_map[k] += (inp or 0) + (out or 0)
    for row in tr_trend_res.all():
        ct, inp, out = row[0], row[1], row[2]
        if ct:
            k = ct.strftime(DATE_FORMAT) if hasattr(ct, "strftime") else str(ct)[:10]
            daily_calls[k] += 1
            daily_tokens_map[k] += (inp or 0) + (out or 0)
    usage_trend = []
    d = date.today() - timedelta(days=days - 1)
    end_d = date.today()
    while d <= end_d:
        k = d.strftime(DATE_FORMAT)
        usage_trend.append(DailyUsageVO(
            date=k,
            api_calls=daily_calls.get(k, 0),
            tokens=daily_tokens_map.get(k, 0)
        ))
        d += timedelta(days=1)
    result = UsageStatisticsVO(
        total_api_calls=total_api_calls,
        today_api_calls=today_api_calls,
        total_tokens=total_tokens,
        total_input_tokens=total_input,
        total_output_tokens=total_output,
        today_tokens=today_tokens,
        usage_by_model=usage_by_model,
        usage_trend=usage_trend
    )
    if redis_client:
        try:
            await redis_client.set(
                cache_key,
                _serialize_for_cache(result.model_dump(mode="json")),
                ex=CacheConstant.STATISTICS_TTL_MINUTES * 60
            )
        except Exception as e:
            logger.warning("写入使用量统计缓存失败: %s", str(e))
    return result


async def get_performance_statistics(
    db: AsyncSession,
    redis_client: Any,
    user_id: int
) -> PerformanceStatisticsVO:
    """获取性能统计数据"""
    cache_key = f"{CacheConstant.STATISTICS_PERFORMANCE_KEY_PREFIX}{user_id}"
    if redis_client:
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                return PerformanceStatisticsVO.model_validate(json.loads(cached))
        except Exception as e:
            logger.warning("读取性能统计缓存失败: %s", str(e))
    msgs_result = await db.execute(
        select(ConversationMessage).where(
            ConversationMessage.user_id == user_id,
            ConversationMessage.role == "assistant",
            ConversationMessage.is_delete == 0,
            ConversationMessage.response_time_ms.isnot(None)
        )
    )
    messages = list(msgs_result.scalars().all())
    tr_result = await db.execute(
        select(TestResult).where(
            TestResult.user_id == user_id,
            TestResult.is_delete == 0,
            TestResult.response_time_ms.isnot(None)
        )
    )
    tr_list = list(tr_result.scalars().all())
    all_responses = []
    for m in messages:
        if m.response_time_ms is not None:
            all_responses.append((m.model_name, m.response_time_ms, m.input_tokens or 0, m.output_tokens or 0))
    for t in tr_list:
        if t.response_time_ms is not None:
            all_responses.append((t.model_name, t.response_time_ms, t.input_tokens or 0, t.output_tokens or 0))
    if not all_responses:
        return PerformanceStatisticsVO()
    times = [r[1] for r in all_responses]
    avg_rt = round(sum(times) / len(times), 2)
    min_rt = min(times)
    max_rt = max(times)
    by_model = defaultdict(list)
    for model_name, rt, inp, out in all_responses:
        by_model[model_name or "unknown"].append((rt, inp, out))
    perf_list = []
    for model_name, items in by_model.items():
        rts = [x[0] for x in items]
        inps = [x[1] for x in items]
        outs = [x[2] for x in items]
        perf_list.append(ModelPerformanceVO(
            model_name=model_name,
            call_count=len(items),
            avg_response_time=round(sum(rts) / len(rts), 2),
            min_response_time=min(rts),
            max_response_time=max(rts),
            avg_input_tokens=round(sum(inps) / len(inps), 2),
            avg_output_tokens=round(sum(outs) / len(outs), 2)
        ))
    perf_list.sort(key=lambda x: x.avg_response_time)
    result = PerformanceStatisticsVO(
        avg_response_time=avg_rt,
        min_response_time=min_rt,
        max_response_time=max_rt,
        performance_by_model=perf_list
    )
    if redis_client:
        try:
            await redis_client.set(
                cache_key,
                _serialize_for_cache(result.model_dump(mode="json")),
                ex=CacheConstant.STATISTICS_TTL_MINUTES * 60
            )
        except Exception as e:
            logger.warning("写入性能统计缓存失败: %s", str(e))
    return result
