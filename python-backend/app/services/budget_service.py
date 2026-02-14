"""
预算服务：成本追踪与预算检查
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import date, datetime
from decimal import Decimal
from typing import Optional, Any

from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.constants import CacheConstant
from app.core.logging_config import logger
from app.models.user import User
from app.models.conversation_message import ConversationMessage
from app.models.test_result import TestResult
from app.schemas.budget import BudgetStatusVO

YEAR_MONTH_FORMAT = "%Y%m"
DEFAULT_ALERT_THRESHOLD = 80


def _get_daily_key(user_id: int) -> str:
    return f"{CacheConstant.USER_DAILY_COST_KEY_PREFIX}{user_id}"


def _get_monthly_key(user_id: int) -> str:
    year_month = date.today().strftime(YEAR_MONTH_FORMAT)
    return f"{CacheConstant.USER_MONTHLY_COST_KEY_PREFIX}{user_id}:{year_month}"


async def add_cost_async(redis_client: Any, user_id: int, cost: Decimal) -> None:
    """
    累加用户消耗（异步，用于对话等场景）
    """
    if redis_client is None or cost is None or cost <= 0:
        return
    daily_key = _get_daily_key(user_id)
    monthly_key = _get_monthly_key(user_id)
    cost_float = float(cost)
    try:
        daily_val = await redis_client.incrbyfloat(daily_key, cost_float)
        if daily_val == cost_float:
            await redis_client.expire(daily_key, CacheConstant.USER_DAILY_COST_TTL_HOURS * 3600)
        monthly_val = await redis_client.incrbyfloat(monthly_key, cost_float)
        if monthly_val == cost_float:
            await redis_client.expire(monthly_key, CacheConstant.USER_MONTHLY_COST_TTL_DAYS * 86400)
        logger.debug("用户 %s 消耗累加成功，本次: %s, 今日: %s, 本月: %s", user_id, cost, daily_val, monthly_val)
    except Exception as e:
        logger.error("用户 %s 消耗累加失败: %s", user_id, str(e), exc_info=True)


def add_cost_sync(redis_client: Any, user_id: int, cost: Decimal) -> None:
    """
    累加用户消耗（同步，用于批量测试 Worker 等场景）
    """
    if redis_client is None or cost is None or cost <= 0:
        return
    daily_key = _get_daily_key(user_id)
    monthly_key = _get_monthly_key(user_id)
    cost_float = float(cost)
    try:
        daily_val = redis_client.incrbyfloat(daily_key, cost_float)
        if daily_val == cost_float:
            redis_client.expire(daily_key, CacheConstant.USER_DAILY_COST_TTL_HOURS * 3600)
        monthly_val = redis_client.incrbyfloat(monthly_key, cost_float)
        if monthly_val == cost_float:
            redis_client.expire(monthly_key, CacheConstant.USER_MONTHLY_COST_TTL_DAYS * 86400)
        logger.debug("用户 %s 消耗累加成功，本次: %s, 今日: %s, 本月: %s", user_id, cost, daily_val, monthly_val)
    except Exception as e:
        logger.error("用户 %s 消耗累加失败: %s", user_id, str(e), exc_info=True)


async def get_today_cost(redis_client: Any, user_id: int) -> Decimal:
    """
    获取用户今日消耗（从 Redis 获取）
    """
    if redis_client is None:
        return Decimal("0")
    daily_key = _get_daily_key(user_id)
    try:
        value = await redis_client.get(daily_key)
        return Decimal(value) if value is not None else Decimal("0")
    except Exception as e:
        logger.warning("获取用户 %s 今日消耗失败: %s", user_id, str(e))
        return Decimal("0")


async def get_month_cost(redis_client: Any, user_id: int) -> Decimal:
    """
    获取用户本月消耗（从 Redis 获取）
    """
    if redis_client is None:
        return Decimal("0")
    monthly_key = _get_monthly_key(user_id)
    try:
        value = await redis_client.get(monthly_key)
        return Decimal(value) if value is not None else Decimal("0")
    except Exception as e:
        logger.warning("获取用户 %s 本月消耗失败: %s", user_id, str(e))
        return Decimal("0")


async def sync_cost_from_db(db: AsyncSession, redis_client: Any, user_id: int) -> None:
    """
    从数据库同步消耗数据到 Redis（缓存未命中时调用）
    """
    if redis_client is None:
        return
    today_start = datetime.combine(date.today(), datetime.min.time())
    first_day = date.today().replace(day=1)
    month_start = datetime.combine(first_day, datetime.min.time())
    if first_day.month == 12:
        next_month = first_day.replace(year=first_day.year + 1, month=1)
    else:
        next_month = first_day.replace(month=first_day.month + 1)
    month_end = datetime.combine(next_month, datetime.min.time())
    try:
        cm_today = await db.execute(
            select(func.coalesce(func.sum(ConversationMessage.cost), 0)).where(
                ConversationMessage.user_id == user_id,
                ConversationMessage.is_delete == 0,
                ConversationMessage.create_time >= today_start
            )
        )
        tr_today = await db.execute(
            select(func.coalesce(func.sum(TestResult.cost), 0)).where(
                TestResult.user_id == user_id,
                TestResult.is_delete == 0,
                TestResult.create_time >= today_start
            )
        )
        today_cost = Decimal(str(cm_today.scalar() or 0)) + Decimal(str(tr_today.scalar() or 0))
        cm_month = await db.execute(
            select(func.coalesce(func.sum(ConversationMessage.cost), 0)).where(
                ConversationMessage.user_id == user_id,
                ConversationMessage.is_delete == 0,
                ConversationMessage.create_time >= month_start,
                ConversationMessage.create_time < month_end
            )
        )
        tr_month = await db.execute(
            select(func.coalesce(func.sum(TestResult.cost), 0)).where(
                TestResult.user_id == user_id,
                TestResult.is_delete == 0,
                TestResult.create_time >= month_start,
                TestResult.create_time < month_end
            )
        )
        month_cost = Decimal(str(cm_month.scalar() or 0)) + Decimal(str(tr_month.scalar() or 0))
        daily_key = _get_daily_key(user_id)
        monthly_key = _get_monthly_key(user_id)
        ttl_hours = CacheConstant.USER_DAILY_COST_TTL_HOURS * 3600
        ttl_days = CacheConstant.USER_MONTHLY_COST_TTL_DAYS * 86400
        if today_cost > 0:
            await redis_client.set(daily_key, str(today_cost), ex=ttl_hours)
        if month_cost > 0:
            await redis_client.set(monthly_key, str(month_cost), ex=ttl_days)
        logger.debug("用户 %s 消耗数据同步成功，今日: %s, 本月: %s", user_id, today_cost, month_cost)
    except Exception as e:
        logger.error("用户 %s 消耗数据同步失败: %s", user_id, str(e), exc_info=True)


async def get_today_cost_with_sync(
    db: AsyncSession, redis_client: Any, user_id: int
) -> Decimal:
    """
    获取今日消耗，若 Redis 无数据则从 DB 同步
    """
    if redis_client is None:
        return Decimal("0")
    daily_key = _get_daily_key(user_id)
    value = await redis_client.get(daily_key)
    if value is None:
        await sync_cost_from_db(db, redis_client, user_id)
        value = await redis_client.get(daily_key)
    return Decimal(value) if value is not None else Decimal("0")


async def get_month_cost_with_sync(
    db: AsyncSession, redis_client: Any, user_id: int
) -> Decimal:
    """
    获取本月消耗，若 Redis 无数据则从 DB 同步
    """
    if redis_client is None:
        return Decimal("0")
    monthly_key = _get_monthly_key(user_id)
    value = await redis_client.get(monthly_key)
    if value is None:
        await sync_cost_from_db(db, redis_client, user_id)
        value = await redis_client.get(monthly_key)
    return Decimal(value) if value is not None else Decimal("0")


async def check_budget(
    db: AsyncSession, redis_client: Any, user_id: int
) -> BudgetStatusVO:
    """
    检查用户预算状态
    """
    result = await db.execute(select(User).where(User.id == user_id, User.is_delete == 0))
    user = result.scalar_one_or_none()
    if user is None:
        return BudgetStatusVO.normal(Decimal("0"), Decimal("0"), None, None)
    daily_budget = user.daily_budget
    monthly_budget = user.monthly_budget
    alert_threshold = user.budget_alert_threshold if user.budget_alert_threshold is not None else DEFAULT_ALERT_THRESHOLD
    today_cost = await get_today_cost_with_sync(db, redis_client, user_id)
    month_cost = await get_month_cost_with_sync(db, redis_client, user_id)
    if daily_budget is not None and daily_budget > 0:
        daily_usage_percent = (today_cost * 100 / daily_budget).quantize(Decimal("0.01"))
        if today_cost >= daily_budget:
            return BudgetStatusVO.exceeded(
                f"今日预算已用完（{today_cost:.2f} / {daily_budget:.2f} USD）",
                today_cost, month_cost, daily_budget, monthly_budget,
                daily_usage_percent=daily_usage_percent
            )
        if daily_usage_percent >= alert_threshold:
            return BudgetStatusVO.warning(
                f"今日预算已使用 {daily_usage_percent:.0f}%（{today_cost:.2f} / {daily_budget:.2f} USD）",
                today_cost, month_cost, daily_budget, monthly_budget,
                daily_usage_percent=daily_usage_percent
            )
    if monthly_budget is not None and monthly_budget > 0:
        monthly_usage_percent = (month_cost * 100 / monthly_budget).quantize(Decimal("0.01"))
        if month_cost >= monthly_budget:
            return BudgetStatusVO.exceeded(
                f"本月预算已用完（{month_cost:.2f} / {monthly_budget:.2f} USD）",
                today_cost, month_cost, daily_budget, monthly_budget,
                monthly_usage_percent=monthly_usage_percent
            )
        if monthly_usage_percent >= alert_threshold:
            return BudgetStatusVO.warning(
                f"本月预算已使用 {monthly_usage_percent:.0f}%（{month_cost:.2f} / {monthly_budget:.2f} USD）",
                today_cost, month_cost, daily_budget, monthly_budget,
                monthly_usage_percent=monthly_usage_percent
            )
    return BudgetStatusVO.normal(today_cost, month_cost, daily_budget, monthly_budget)
