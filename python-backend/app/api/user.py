"""
用户接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.user import (
    UserRegisterRequest,
    UserLoginRequest,
    UserAddRequest,
    UserUpdateRequest,
    UserUpdateMyRequest,
    UserQueryRequest,
    LoginUserVO,
    UserVO,
    UserStatisticsVO
)
from app.schemas.budget import BudgetStatusVO, BudgetUpdateRequest
from app.schemas.common import DeleteRequest, PageRequest
from app.services.user_service import UserService
from app.services.budget_service import check_budget


def _get_redis(request: Request):
    if request is None:
        return None
    return getattr(request.app.state, "redis_client", None)

router = APIRouter(prefix="/user", tags=["用户接口"])


@router.post("/register", response_model=BaseResponse[int], summary="用户注册")
async def user_register(
    request: UserRegisterRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    用户注册接口
    
    Args:
        request: 注册请求参数
        db: 数据库会话
        
    Returns:
        注册成功的用户ID
    """
    user_id = await UserService.user_register(
        db,
        request.user_account,
        request.user_password,
        request.check_password
    )
    return BaseResponse(code=0, data=user_id, message="ok")


@router.post("/login", response_model=BaseResponse[LoginUserVO], summary="用户登录")
async def user_login(
    login_request: UserLoginRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    用户登录接口
    
    Args:
        login_request: 登录请求参数
        request: 请求对象
        db: 数据库会话
        
    Returns:
        登录用户信息
    """
    login_user = await UserService.user_login(
        db,
        request,
        login_request.user_account,
        login_request.user_password
    )
    return BaseResponse(code=0, data=login_user, message="ok")


@router.get("/statistics", response_model=BaseResponse[UserStatisticsVO], summary="获取用户统计数据")
async def get_user_statistics(
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取用户统计数据（模型数、Token、成本、预算等）
    需要登录
    """
    user = await UserService.get_login_user(db, request)
    statistics = await UserService.get_user_statistics(db, user.id)
    return BaseResponse(code=0, data=statistics, message="ok")


@router.get("/budget/status", response_model=BaseResponse[BudgetStatusVO], summary="获取预算状态")
async def get_budget_status(
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取当前用户预算状态（今日/本月消耗、预警等）
    需要登录
    """
    user = await UserService.get_login_user(db, request)
    budget_status = await check_budget(db, _get_redis(request), user.id)
    return BaseResponse(code=0, data=budget_status, message="ok")


@router.post("/update/my", response_model=BaseResponse[bool], summary="更新当前登录用户信息")
async def update_my_info(
    body: UserUpdateMyRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新当前登录用户信息（昵称、头像、简介、预算设置）
    需要登录
    """
    user = await UserService.get_login_user(db, request)
    await UserService.update_my_info(
        db,
        user.id,
        user_name=body.user_name,
        user_avatar=body.user_avatar,
        user_profile=body.user_profile,
        daily_budget=body.daily_budget,
        monthly_budget=body.monthly_budget,
        budget_alert_threshold=body.budget_alert_threshold
    )
    return BaseResponse(code=0, data=True, message="ok")


@router.post("/budget/update", response_model=BaseResponse[bool], summary="更新预算设置")
async def update_budget(
    body: BudgetUpdateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新当前用户预算设置（日预算、月预算、预警阈值）
    需要登录
    """
    user = await UserService.get_login_user(db, request)
    await UserService.update_budget(
        db,
        user.id,
        daily_budget=body.daily_budget,
        monthly_budget=body.monthly_budget,
        alert_threshold=body.alert_threshold
    )
    return BaseResponse(code=0, data=True, message="ok")


@router.get("/get/login", response_model=BaseResponse[LoginUserVO], summary="获取当前登录用户")
async def get_login_user(
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取当前登录用户
    
    Args:
        request: 请求对象
        db: 数据库会话
        
    Returns:
        当前登录用户信息
    """
    user = await UserService.get_login_user(db, request)
    login_user_vo = UserService.get_login_user_vo(user)
    return BaseResponse(code=0, data=login_user_vo, message="ok")


@router.post("/logout", response_model=BaseResponse[bool], summary="用户登出")
async def user_logout(request: Request):
    """
    用户登出接口
    
    Args:
        request: 请求对象
        
    Returns:
        是否成功
    """
    result = await UserService.user_logout(request)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/add", response_model=BaseResponse[int], summary="添加用户（管理员）")
async def add_user(
    user_request: UserAddRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    添加用户接口（仅管理员）
    
    Args:
        user_request: 用户信息
        request: 请求对象
        db: 数据库会话
        
    Returns:
        用户ID
    """
    await UserService.check_admin(db, request)
    user_id = await UserService.add_user(db, user_request)
    return BaseResponse(code=0, data=user_id, message="ok")


@router.get("/get", response_model=BaseResponse[dict], summary="获取用户详情（管理员）")
async def get_user_by_id(
    id: int,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    根据ID获取用户详情（仅管理员）
    
    Args:
        id: 用户ID
        request: 请求对象
        db: 数据库会话
        
    Returns:
        用户详情
    """
    await UserService.check_admin(db, request)
    user = await UserService.get_user_by_id(db, id)
    return BaseResponse(code=0, data=user, message="ok")


@router.get("/get/vo", response_model=BaseResponse[UserVO], summary="获取用户VO")
async def get_user_vo_by_id(
    id: int,
    db: AsyncSession = Depends(get_db)
):
    """
    根据ID获取用户VO（脱敏）
    
    Args:
        id: 用户ID
        db: 数据库会话
        
    Returns:
        用户VO
    """
    user_vo = await UserService.get_user_vo_by_id(db, id)
    return BaseResponse(code=0, data=user_vo, message="ok")


@router.post("/delete", response_model=BaseResponse[bool], summary="删除用户（管理员）")
async def delete_user(
    delete_request: DeleteRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    删除用户接口（仅管理员）
    
    Args:
        delete_request: 删除请求
        request: 请求对象
        db: 数据库会话
        
    Returns:
        是否成功
    """
    await UserService.check_admin(db, request)
    result = await UserService.delete_user(db, delete_request.id)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/update", response_model=BaseResponse[bool], summary="更新用户（管理员）")
async def update_user(
    user_request: UserUpdateRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    更新用户接口（仅管理员）
    
    Args:
        user_request: 更新请求
        request: 请求对象
        db: 数据库会话
        
    Returns:
        是否成功
    """
    await UserService.check_admin(db, request)
    result = await UserService.update_user(db, user_request)
    return BaseResponse(code=0, data=result, message="ok")


@router.post("/list/page/vo", response_model=BaseResponse[dict], summary="分页查询用户（管理员）")
async def list_user_vo_by_page(
    query_request: UserQueryRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询用户列表（仅管理员）
    
    Args:
        query_request: 查询请求
        request: 请求对象
        db: 数据库会话
        
    Returns:
        分页用户列表
    """
    await UserService.check_admin(db, request)
    page_result = await UserService.list_user_vo_by_page(db, query_request)
    return BaseResponse(code=0, data=page_result, message="ok")
