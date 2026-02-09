"""
提示词模板接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.prompt import (
    CreatePromptTemplateRequest,
    UpdatePromptTemplateRequest,
    PromptTemplateDeleteRequest,
)
from app.services.prompt_template_service import PromptTemplateService
from app.services.user_service import UserService

router = APIRouter(prefix="/prompt/template", tags=["提示词模板接口"])


@router.get("/list", response_model=BaseResponse[list], summary="获取模板列表")
async def list_templates(
    request: Request,
    strategy: str | None = Query(default=None, description="策略类型"),
    db: AsyncSession = Depends(get_db),
):
    """
    获取模板列表（预设 + 用户自定义）
    """
    user = await UserService.get_login_user(db, request)
    templates = await PromptTemplateService.list_templates(db, user.id, strategy)
    return BaseResponse(code=0, data=[t.model_dump(by_alias=True) for t in templates], message="ok")


@router.get("/get", response_model=BaseResponse[dict], summary="根据ID获取模板")
async def get_template(
    request: Request,
    templateId: str = Query(..., description="模板ID"),
    db: AsyncSession = Depends(get_db),
):
    """
    根据 ID 获取模板
    """
    if not templateId or not templateId.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空")
    user = await UserService.get_login_user(db, request)
    template = await PromptTemplateService.get_template_by_id(db, templateId.strip(), user.id)
    return BaseResponse(code=0, data=template.model_dump(by_alias=True), message="ok")


@router.post("/create", response_model=BaseResponse[str], summary="创建模板")
async def create_template(
    request_body: CreatePromptTemplateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    创建模板
    """
    user = await UserService.get_login_user(db, request)
    data = request_body.model_dump(exclude_none=True)
    template_id = await PromptTemplateService.create_template(
        db,
        name=data["name"],
        strategy=data["strategy"],
        content=data["content"],
        user_id=user.id,
        description=data.get("description"),
        variables=data.get("variables"),
        category=data.get("category"),
    )
    return BaseResponse(code=0, data=template_id, message="ok")


@router.post("/update", response_model=BaseResponse[bool], summary="更新模板")
async def update_template(
    request_body: UpdatePromptTemplateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    更新模板
    """
    user = await UserService.get_login_user(db, request)
    data = request_body.model_dump(exclude_none=True, by_alias=True)
    template_id = data.pop("id")
    if "isActive" in data:
        data["is_active"] = data.pop("isActive")
    result = await PromptTemplateService.update_template(
        db,
        template_id=template_id,
        user_id=user.id,
        name=data.get("name"),
        description=data.get("description"),
        strategy=data.get("strategy"),
        content=data.get("content"),
        variables=data.get("variables"),
        category=data.get("category"),
        is_active=data.get("is_active"),
    )
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/delete", response_model=BaseResponse[bool], summary="删除模板")
async def delete_template(
    delete_request: PromptTemplateDeleteRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """
    删除模板
    """
    user = await UserService.get_login_user(db, request)
    result = await PromptTemplateService.delete_template(db, delete_request.id, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/increment-usage", response_model=BaseResponse[bool], summary="增加使用次数")
async def increment_usage(
    templateId: str = Query(..., description="模板ID"),
    db: AsyncSession = Depends(get_db),
):
    """
    增加使用次数（不校验登录，便于前端统计）
    """
    if not templateId or not templateId.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空")
    result = await PromptTemplateService.increment_usage_count(db, templateId.strip())
    return BaseResponse(code=0, data=result, message="ok")
