"""
场景管理接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.scene import (
    CreateSceneRequest,
    UpdateSceneRequest,
    SceneDeleteRequest,
    AddScenePromptRequest,
    UpdateScenePromptRequest,
)
from app.services.scene_service import SceneService
from app.services.user_service import UserService

router = APIRouter(prefix="/scene", tags=["场景管理接口"])


@router.post("/create", response_model=BaseResponse[str], summary="创建场景")
async def create_scene(
    request_body: CreateSceneRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    创建场景

    Args:
        request_body: 创建请求参数
        request: 请求对象
        db: 数据库会话

    Returns:
        场景ID
    """
    user = await UserService.get_login_user(db, request)
    scene_data = request_body.model_dump(exclude_none=True)
    scene_id = await SceneService.create_scene(db, scene_data, user.id)
    return BaseResponse(code=0, data=scene_id, message="ok")


@router.post("/update", response_model=BaseResponse[bool], summary="更新场景")
async def update_scene(
    request_body: UpdateSceneRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新场景

    Args:
        request_body: 更新请求参数
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    scene_data = request_body.model_dump(exclude_none=True, by_alias=True)
    if "isActive" in scene_data:
        scene_data["is_active"] = scene_data.pop("isActive")
    result = await SceneService.update_scene(db, scene_data, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/delete", response_model=BaseResponse[bool], summary="删除场景")
async def delete_scene(
    delete_request: SceneDeleteRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    删除场景

    Args:
        delete_request: 删除请求
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    result = await SceneService.delete_scene(db, delete_request.id, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.get("/get", response_model=BaseResponse[dict], summary="获取场景详情")
async def get_scene(
    id: str,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取场景详情

    Args:
        id: 场景ID
        request: 请求对象
        db: 数据库会话

    Returns:
        场景信息
    """
    if not id or not id.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空")

    user = await UserService.get_login_user(db, request)
    scene = await SceneService.get_scene(db, id.strip(), user.id)
    return BaseResponse(code=0, data=SceneService._scene_to_dict(scene), message="ok")


@router.get("/prompts", response_model=BaseResponse[list], summary="获取场景提示词列表")
async def get_scene_prompts(
    sceneId: str,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取场景下的所有提示词

    Args:
        sceneId: 场景ID
        request: 请求对象
        db: 数据库会话

    Returns:
        提示词列表
    """
    if not sceneId or not sceneId.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空")

    user = await UserService.get_login_user(db, request)
    prompts = await SceneService.get_scene_prompts(db, sceneId.strip(), user.id)
    data = [SceneService._scene_prompt_to_dict(p) for p in prompts]
    return BaseResponse(code=0, data=data, message="ok")


@router.post("/prompt/add", response_model=BaseResponse[str], summary="添加场景提示词")
async def add_scene_prompt(
    request_body: AddScenePromptRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    添加场景提示词

    Args:
        request_body: 添加请求参数
        request: 请求对象
        db: 数据库会话

    Returns:
        提示词ID
    """
    user = await UserService.get_login_user(db, request)
    data = request_body.model_dump(exclude_none=True, by_alias=True)
    if "sceneId" in data:
        data["scene_id"] = data.pop("sceneId")
    if "expectedOutput" in data:
        data["expected_output"] = data.pop("expectedOutput")
    prompt_id = await SceneService.add_scene_prompt(db, data, user.id)
    return BaseResponse(code=0, data=prompt_id, message="ok")


@router.post("/prompt/update", response_model=BaseResponse[bool], summary="更新场景提示词")
async def update_scene_prompt(
    request_body: UpdateScenePromptRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新场景提示词

    Args:
        request_body: 更新请求参数
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    data = request_body.model_dump(exclude_none=True, by_alias=True)
    if "expectedOutput" in data:
        data["expected_output"] = data.pop("expectedOutput")
    result = await SceneService.update_scene_prompt(db, data, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/prompt/delete", response_model=BaseResponse[bool], summary="删除场景提示词")
async def delete_scene_prompt(
    delete_request: SceneDeleteRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    删除场景提示词

    Args:
        delete_request: 删除请求（id 为提示词ID）
        request: 请求对象
        db: 数据库会话

    Returns:
        是否成功
    """
    user = await UserService.get_login_user(db, request)
    result = await SceneService.delete_scene_prompt(db, delete_request.id, user.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.get("/list/page", response_model=BaseResponse[dict], summary="分页查询场景列表")
async def list_scenes(
    pageNum: int = 1,
    pageSize: int = 10,
    category: str | None = None,
    isPreset: bool | None = None,
    request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询场景列表

    Args:
        pageNum: 页码
        pageSize: 每页大小
        category: 分类
        isPreset: 是否预设
        request: 请求对象
        db: 数据库会话

    Returns:
        分页结果
    """
    user = await UserService.get_login_user(db, request)
    page_result = await SceneService.list_scenes(
        db,
        user.id,
        page_num=pageNum,
        page_size=pageSize,
        category=category,
        is_preset=isPreset
    )
    return BaseResponse(code=0, data=page_result, message="ok")
