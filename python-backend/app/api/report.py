"""
报告接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request

from app.core.errors import BusinessException, ErrorCode
from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.report import ReportVO
from app.services.report_service import ReportService
from app.services.user_service import UserService
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter(prefix="/report", tags=["报告接口"])


@router.get("/generate", response_model=BaseResponse[ReportVO], summary="生成测试报告")
async def generate_report(
    taskId: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    根据任务ID生成多维度对比报告（摘要、模型统计、雷达图、柱状图、详细结果）。
    需登录且仅可查看本人任务。
    """
    if not taskId or not taskId.strip():
        raise BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空")

    user = await UserService.get_login_user(db, request)
    report = await ReportService.generate_report(db, taskId.strip(), user.id)
    return BaseResponse(code=0, data=report, message="ok")
