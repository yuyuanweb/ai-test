"""
提示词优化接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.prompt import PromptOptimizationRequest, PromptOptimizationVO
from app.services.prompt_optimization_service import PromptOptimizationService
from app.services.user_service import UserService

router = APIRouter(prefix="/prompt/optimization", tags=["提示词优化接口"])

_optimization_service: PromptOptimizationService | None = None


def _get_redis(request: Request):
    return getattr(request.app.state, "redis_client", None)


def get_optimization_service() -> PromptOptimizationService:
    """
    获取提示词优化服务单例
    """
    global _optimization_service
    if _optimization_service is None:
        _optimization_service = PromptOptimizationService()
    return _optimization_service


@router.post("/analyze", response_model=BaseResponse[dict], summary="分析并优化提示词")
async def optimize_prompt(
    request_body: PromptOptimizationRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    分析并优化提示词，返回问题列表、优化后提示词与改进点
    """
    from app.core.errors import BusinessException, ErrorCode
    if request_body is None:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空")

    user = await UserService.get_login_user(db, request)
    service = get_optimization_service()

    from app.core.logging_config import logger
    logger.info(
        "提示词优化请求: user=%s, promptLength=%s, hasResponse=%s",
        user.id,
        len(request_body.original_prompt) if request_body.original_prompt else 0,
        bool(request_body.ai_response and request_body.ai_response.strip()),
    )

    result = await service.optimize_prompt(
        original_prompt=request_body.original_prompt,
        ai_response=request_body.ai_response,
        evaluation_model=request_body.evaluation_model,
        user_id=user.id,
        db=db,
        redis_client=_get_redis(request),
    )
    return BaseResponse(code=0, data=result.model_dump(by_alias=True), message="ok")
