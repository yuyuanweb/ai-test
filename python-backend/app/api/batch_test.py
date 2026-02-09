"""
批量测试接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.batch_test import (
    CreateBatchTestRequest,
    TaskQueryRequest,
    UpdateTestResultRatingRequest,
    BatchTestDeleteRequest
)
from app.services.batch_test_service import BatchTestService
from app.services.user_service import UserService
from app.utils.rate_limit import check_rate_limit, RateLimitType

router = APIRouter(prefix="/batch-test", tags=["批量测试接口"])


def _get_redis(request: Request):
    if request is None:
        return None
    return getattr(request.app.state, "redis_client", None)


@router.post("/create", response_model=BaseResponse[str], summary="创建批量测试任务")
async def create_batch_test_task(
    request_body: CreateBatchTestRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    创建批量测试任务

    Args:
        request_body: 创建请求参数
        request: 请求对象
        db: 数据库会话

    Returns:
        任务ID
    """
    await check_rate_limit(
        _get_redis(request), request,
        RateLimitType.USER, 3, 60,
        message="批量测试创建过于频繁，请稍后再试"
    )
    user = await UserService.get_login_user(db, request)
    req_data = request_body.model_dump(exclude_none=True)
    if "sceneId" in req_data:
        req_data["scene_id"] = req_data.pop("sceneId")
    if "topP" in req_data:
        req_data["top_p"] = req_data.pop("topP")
    if "maxTokens" in req_data:
        req_data["max_tokens"] = req_data.pop("maxTokens")
    if "topK" in req_data:
        req_data["top_k"] = req_data.pop("topK")
    if "frequencyPenalty" in req_data:
        req_data["frequency_penalty"] = req_data.pop("frequencyPenalty")
    if "presencePenalty" in req_data:
        req_data["presence_penalty"] = req_data.pop("presencePenalty")
    if "enableAiScoring" in req_data:
        req_data["enable_ai_scoring"] = req_data.pop("enableAiScoring")

    task_id = await BatchTestService.create_batch_test_task(db, req_data, user.id)
    return BaseResponse(code=0, data=task_id, message="ok")


@router.get("/task/get", response_model=BaseResponse[dict], summary="获取任务详情")
async def get_task(
    taskId: str,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取任务详情

    Args:
        taskId: 任务ID
        request: 请求对象
        db: 数据库会话

    Returns:
        任务信息
    """
    if not taskId or not taskId.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空")

    user = await UserService.get_login_user(db, request)
    task = await BatchTestService.get_task(db, taskId.strip(), user.id)
    return BaseResponse(code=0, data=BatchTestService._task_to_dict(task), message="ok")


@router.post("/task/list/page", response_model=BaseResponse[dict], summary="分页查询任务列表")
async def list_tasks(
    query_request: TaskQueryRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询任务列表

    Args:
        query_request: 查询请求
        request: 请求对象
        db: 数据库会话

    Returns:
        分页结果
    """
    user = await UserService.get_login_user(db, request)
    query_data = query_request.model_dump(exclude_none=True)

    page_result = await BatchTestService.list_tasks(db, user.id, query_data)
    return BaseResponse(code=0, data=page_result, message="ok")


@router.post("/task/delete", response_model=BaseResponse[bool], summary="删除任务")
async def delete_task(
    delete_request: BatchTestDeleteRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    删除任务

    Args:
        delete_request: 删除请求
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    result = await BatchTestService.delete_task(db, delete_request.id, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.get("/result/list", response_model=BaseResponse[list], summary="获取任务的测试结果")
async def get_task_results(
    taskId: str,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取任务的测试结果

    Args:
        taskId: 任务ID
        request: 请求对象
        db: 数据库会话

    Returns:
        测试结果列表
    """
    if not taskId or not taskId.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空")

    user = await UserService.get_login_user(db, request)
    results = await BatchTestService.get_task_results(db, taskId.strip(), user.id)
    return BaseResponse(code=0, data=results, message="ok")


@router.post("/result/rating", response_model=BaseResponse[bool], summary="更新测试结果评分")
async def update_test_result_rating(
    rating_request: UpdateTestResultRatingRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新测试结果评分

    Args:
        rating_request: 评分请求
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    req_data = rating_request.model_dump(exclude_none=True)
    result_id = req_data.get("result_id") or req_data.get("resultId", "")
    user_rating = req_data.get("user_rating") if "user_rating" in req_data else req_data.get("userRating")
    result = await BatchTestService.update_test_result_rating(db, result_id, user_rating, user.id)
    return BaseResponse(code=0, data=result, message="ok")
