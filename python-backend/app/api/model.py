"""
模型接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import List
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.model import ModelQueryRequest, ModelVO
from app.services.user_service import UserService
from app.services.model_service import ModelService

router = APIRouter(prefix="/model", tags=["模型接口"])


@router.post("/list", response_model=BaseResponse[dict], summary="分页查询模型列表（POST）")
async def list_models_post(
    query_request: ModelQueryRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询模型列表（支持搜索）
    """
    user_id = None
    try:
        login_user = await UserService.get_login_user(db, http_request)
        user_id = login_user.id
    except Exception:
        pass
    
    model_service = ModelService(db)
    models, total = await model_service.list_models(query_request, user_id)
    current = query_request.page_num if query_request.page_num is not None else query_request.current
    return BaseResponse(
        code=0,
        data={
            "records": models,
            "total": total,
            "current": current,
            "pageSize": query_request.page_size
        },
        message="ok"
    )


@router.get("/list", response_model=BaseResponse[dict], summary="分页查询模型列表（GET）")
async def list_models_get(
    current: int = 1,
    pageSize: int = 50,
    searchText: str = None,
    provider: str = None,
    http_request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询模型列表（支持搜索）- GET方式
    """
    user_id = None
    try:
        login_user = await UserService.get_login_user(db, http_request)
        user_id = login_user.id
    except Exception:
        pass
    
    query_request = ModelQueryRequest(
        current=current,
        page_size=pageSize,
        search_text=searchText,
        provider=provider
    )
    
    model_service = ModelService(db)
    models, total = await model_service.list_models(query_request, user_id)
    
    return BaseResponse(
        code=0,
        data={
            "records": models,
            "total": total,
            "current": current,
            "pageSize": pageSize
        },
        message="ok"
    )


@router.get("/all", response_model=BaseResponse[List[ModelVO]], summary="获取所有模型列表")
async def get_all_models(
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取所有模型列表（国内优先）
    """
    user_id = None
    try:
        login_user = await UserService.get_login_user(db, http_request)
        user_id = login_user.id
    except Exception:
        pass
    
    model_service = ModelService(db)
    models = await model_service.get_all_models(user_id)
    
    return BaseResponse(code=0, data=models, message="ok")


@router.post("/sync", response_model=BaseResponse[int], summary="从OpenRouter同步模型")
async def sync_models(
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    从 OpenRouter 同步模型列表（仅管理员）
    """
    login_user = await UserService.get_login_user(db, http_request)
    
    if login_user.user_role != "admin":
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限")
    
    model_service = ModelService(db)
    count = await model_service.sync_models_from_openrouter()
    
    return BaseResponse(code=0, data=count, message=f"成功同步 {count} 个模型")
