"""
用户-模型使用统计服务
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from decimal import Decimal
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import Session

from app.core.logging_config import logger
from app.models.user_model_usage import UserModelUsage


async def update_user_model_usage(
    db: AsyncSession,
    user_id: int,
    model_name: str,
    tokens: int,
    cost: Decimal
) -> None:
    """
    更新用户-模型使用统计（累加）
    """
    if user_id is None or not model_name or not model_name.strip() or tokens <= 0:
        return
    try:
        result = await db.execute(
            select(UserModelUsage).where(
                UserModelUsage.user_id == user_id,
                UserModelUsage.model_name == model_name.strip(),
                UserModelUsage.is_delete == 0
            )
        )
        usage = result.scalar_one_or_none()
        cost_val = cost if cost is not None else Decimal('0')
        if usage is None:
            usage = UserModelUsage(
                id=str(uuid.uuid4()),
                user_id=user_id,
                model_name=model_name.strip(),
                total_tokens=tokens,
                total_cost=cost_val
            )
            db.add(usage)
        else:
            usage.total_tokens = (usage.total_tokens or 0) + tokens
            usage.total_cost = (usage.total_cost or Decimal('0')) + cost_val
        await db.commit()
        logger.debug(
            "更新用户模型使用统计: userId=%s, modelName=%s, addTokens=%s, addCost=%s, totalTokens=%s, totalCost=%s",
            user_id, model_name, tokens, cost, usage.total_tokens, usage.total_cost
        )
    except Exception as e:
        logger.error("更新用户-模型使用统计失败: userId=%s, modelName=%s, error=%s", user_id, model_name, str(e), exc_info=True)
        await db.rollback()


def update_user_model_usage_sync(
    session: Session,
    user_id: int,
    model_name: str,
    tokens: int,
    cost: Decimal
) -> None:
    """
    更新用户-模型使用统计（同步，用于批量测试 Worker）
    """
    if user_id is None or not model_name or not model_name.strip() or tokens <= 0:
        return
    try:
        result = session.execute(
            select(UserModelUsage).where(
                UserModelUsage.user_id == user_id,
                UserModelUsage.model_name == model_name.strip(),
                UserModelUsage.is_delete == 0
            )
        )
        usage = result.scalar_one_or_none()
        cost_val = cost if cost is not None else Decimal('0')
        if usage is None:
            usage = UserModelUsage(
                id=str(uuid.uuid4()),
                user_id=user_id,
                model_name=model_name.strip(),
                total_tokens=tokens,
                total_cost=cost_val
            )
            session.add(usage)
        else:
            usage.total_tokens = (usage.total_tokens or 0) + tokens
            usage.total_cost = (usage.total_cost or Decimal('0')) + cost_val
        session.commit()
        logger.debug(
            "更新用户模型使用统计: userId=%s, modelName=%s, addTokens=%s, addCost=%s",
            user_id, model_name, tokens, cost
        )
    except Exception as e:
        logger.error("更新用户-模型使用统计失败: userId=%s, modelName=%s, error=%s", user_id, model_name, str(e), exc_info=True)
        session.rollback()
