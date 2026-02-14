"""
统计接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.statistics import (
    CostStatisticsVO,
    UsageStatisticsVO,
    PerformanceStatisticsVO,
    RealtimeCostVO
)
from app.services.user_service import UserService
from app.services.statistics_service import (
    get_realtime_cost,
    get_cost_statistics,
    get_usage_statistics,
    get_performance_statistics
)

router = APIRouter(prefix="/statistics", tags=["统计接口"])


def _get_redis(request: Request):
    if request is None:
        return None
    return getattr(request.app.state, "redis_client", None)


@router.get("/realtime", response_model=BaseResponse[RealtimeCostVO], summary="获取实时成本监控")
async def get_realtime(
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """获取实时成本监控数据"""
    user = await UserService.get_login_user(db, request)
    data = await get_realtime_cost(db, _get_redis(request), user.id)
    return BaseResponse(code=0, data=data, message="ok")


@router.get("/cost", response_model=BaseResponse[CostStatisticsVO], summary="获取成本统计")
async def get_cost(
    request: Request,
    days: int = 30,
    db: AsyncSession = Depends(get_db)
):
    """获取成本统计数据"""
    user = await UserService.get_login_user(db, request)
    data = await get_cost_statistics(db, _get_redis(request), user.id, days)
    return BaseResponse(code=0, data=data, message="ok")


@router.get("/usage", response_model=BaseResponse[UsageStatisticsVO], summary="获取使用统计")
async def get_usage(
    request: Request,
    days: int = 30,
    db: AsyncSession = Depends(get_db)
):
    """获取使用量统计数据"""
    user = await UserService.get_login_user(db, request)
    data = await get_usage_statistics(db, _get_redis(request), user.id, days)
    return BaseResponse(code=0, data=data, message="ok")


@router.get("/performance", response_model=BaseResponse[PerformanceStatisticsVO], summary="获取性能统计")
async def get_performance(
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """获取性能统计数据"""
    user = await UserService.get_login_user(db, request)
    data = await get_performance_statistics(db, _get_redis(request), user.id)
    return BaseResponse(code=0, data=data, message="ok")
