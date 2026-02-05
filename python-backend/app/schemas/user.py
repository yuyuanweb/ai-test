"""
用户相关的请求和响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field, field_validator


class UserRegisterRequest(BaseModel):
    """
    用户注册请求
    """
    user_account: str = Field(..., min_length=4, description="用户账号", alias="userAccount")
    user_password: str = Field(..., min_length=8, description="用户密码", alias="userPassword")
    check_password: str = Field(..., min_length=8, description="确认密码", alias="checkPassword")

    class Config:
        populate_by_name = True


class UserLoginRequest(BaseModel):
    """
    用户登录请求
    """
    user_account: str = Field(..., min_length=4, description="用户账号", alias="userAccount")
    user_password: str = Field(..., min_length=8, description="用户密码", alias="userPassword")

    class Config:
        populate_by_name = True


class UserVO(BaseModel):
    """
    用户视图对象(脱敏)
    """
    id: int = Field(..., description="用户ID")
    user_name: Optional[str] = Field(None, description="用户昵称", alias="userName")
    user_avatar: Optional[str] = Field(None, description="用户头像", alias="userAvatar")
    user_profile: Optional[str] = Field(None, description="用户简介", alias="userProfile")
    user_role: str = Field(..., description="用户角色", alias="userRole")
    create_time: datetime = Field(..., description="创建时间", alias="createTime")

    class Config:
        populate_by_name = True
        from_attributes = True


class LoginUserVO(BaseModel):
    """
    登录用户视图对象
    """
    id: int = Field(..., description="用户ID")
    user_account: str = Field(..., description="用户账号", alias="userAccount")
    user_name: Optional[str] = Field(None, description="用户昵称", alias="userName")
    user_avatar: Optional[str] = Field(None, description="用户头像", alias="userAvatar")
    user_profile: Optional[str] = Field(None, description="用户简介", alias="userProfile")
    user_role: str = Field(..., description="用户角色", alias="userRole")
    daily_budget: Optional[Decimal] = Field(None, description="日预算限额", alias="dailyBudget")
    monthly_budget: Optional[Decimal] = Field(None, description="月预算限额", alias="monthlyBudget")
    budget_alert_threshold: int = Field(default=80, description="预算预警阈值", alias="budgetAlertThreshold")
    create_time: datetime = Field(..., description="创建时间", alias="createTime")

    class Config:
        populate_by_name = True
        from_attributes = True


class UserUpdateRequest(BaseModel):
    """
    用户更新请求
    """
    id: int = Field(..., description="用户ID", gt=0)
    user_name: Optional[str] = Field(None, description="用户昵称", alias="userName")
    user_avatar: Optional[str] = Field(None, description="用户头像", alias="userAvatar")
    user_profile: Optional[str] = Field(None, description="用户简介", alias="userProfile")

    class Config:
        populate_by_name = True


class UserAddRequest(BaseModel):
    """
    添加用户请求（管理员）
    """
    user_account: str = Field(..., min_length=4, description="用户账号", alias="userAccount")
    user_name: Optional[str] = Field(None, description="用户昵称", alias="userName")
    user_avatar: Optional[str] = Field(None, description="用户头像", alias="userAvatar")
    user_role: Optional[str] = Field("user", description="用户角色", alias="userRole")

    class Config:
        populate_by_name = True


class UserQueryRequest(BaseModel):
    """
    用户查询请求
    """
    id: Optional[int] = Field(None, description="用户ID")
    user_account: Optional[str] = Field(None, description="用户账号", alias="userAccount")
    user_name: Optional[str] = Field(None, description="用户昵称", alias="userName")
    user_role: Optional[str] = Field(None, description="用户角色", alias="userRole")
    current: int = Field(1, description="当前页码", ge=1)
    page_size: int = Field(10, description="每页大小", ge=1, le=100, alias="pageSize")

    class Config:
        populate_by_name = True
