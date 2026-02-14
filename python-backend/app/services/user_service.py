"""
用户服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import date, datetime
from decimal import Decimal
from typing import Optional, Dict, Any, List
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import Request

from app.models.user import User
from app.models.model import Model
from app.models.conversation_message import ConversationMessage
from app.models.test_result import TestResult
from app.schemas.user import LoginUserVO, UserVO, UserAddRequest, UserUpdateRequest, UserQueryRequest, UserStatisticsVO
from app.utils.password import encrypt_password
from app.core.errors import BusinessException, ErrorCode


USER_LOGIN_STATE = "user_login_state"


class UserService:
    """
    用户服务类
    """

    @staticmethod
    async def user_register(
        db: AsyncSession,
        user_account: str,
        user_password: str,
        check_password: str
    ) -> int:
        """
        用户注册
        
        Args:
            db: 数据库会话
            user_account: 用户账号
            user_password: 用户密码
            check_password: 确认密码
            
        Returns:
            用户ID
            
        Raises:
            BusinessException: 业务异常
        """
        if not user_account or not user_password or not check_password:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "参数为空")
        
        if len(user_account) < 4:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短")
        
        if len(user_password) < 8 or len(check_password) < 8:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短")
        
        if user_password != check_password:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致")
        
        result = await db.execute(
            select(User).where(User.user_account == user_account, User.is_delete == 0)
        )
        existing_user = result.scalar_one_or_none()
        if existing_user:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "账号重复")
        
        encrypt_pwd = encrypt_password(user_password)
        
        new_user = User(
            user_account=user_account,
            user_password=encrypt_pwd,
            user_name="无名",
            user_role="user"
        )
        db.add(new_user)
        await db.commit()
        await db.refresh(new_user)
        
        return new_user.id

    @staticmethod
    async def user_login(
        db: AsyncSession,
        request: Request,
        user_account: str,
        user_password: str
    ) -> LoginUserVO:
        """
        用户登录
        
        Args:
            db: 数据库会话
            request: 请求对象
            user_account: 用户账号
            user_password: 用户密码
            
        Returns:
            登录用户信息
            
        Raises:
            BusinessException: 业务异常
        """
        if not user_account or not user_password:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "参数为空")
        
        if len(user_account) < 4:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短")
        
        if len(user_password) < 8:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短")
        
        encrypt_pwd = encrypt_password(user_password)
        
        result = await db.execute(
            select(User).where(
                User.user_account == user_account,
                User.user_password == encrypt_pwd,
                User.is_delete == 0
            )
        )
        user = result.scalar_one_or_none()
        
        if not user:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误")
        
        request.state.session[USER_LOGIN_STATE] = {
            "id": user.id,
            "user_account": user.user_account,
            "user_role": user.user_role
        }
        
        return LoginUserVO.model_validate(user)

    @staticmethod
    async def get_login_user(db: AsyncSession, request: Request) -> User:
        """
        获取当前登录用户
        
        Args:
            db: 数据库会话
            request: 请求对象
            
        Returns:
            当前登录用户
            
        Raises:
            BusinessException: 未登录异常
        """
        user_info = request.state.session.get(USER_LOGIN_STATE)
        if not user_info or not user_info.get("id"):
            raise BusinessException(ErrorCode.NOT_LOGIN_ERROR)
        
        user_id = user_info["id"]
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        
        if not user:
            raise BusinessException(ErrorCode.NOT_LOGIN_ERROR)
        
        return user

    @staticmethod
    def get_login_user_vo(user: User) -> LoginUserVO:
        """
        获取登录用户VO
        
        Args:
            user: 用户实体
            
        Returns:
            登录用户VO
        """
        if not user:
            return None
        return LoginUserVO.model_validate(user)

    @staticmethod
    async def user_logout(request: Request) -> bool:
        """
        用户登出
        
        Args:
            request: 请求对象
            
        Returns:
            是否成功
        """
        if USER_LOGIN_STATE not in request.state.session:
            raise BusinessException(ErrorCode.OPERATION_ERROR, "未登录")
        
        request.state.session.pop(USER_LOGIN_STATE, None)
        return True

    @staticmethod
    async def check_admin(db: AsyncSession, request: Request):
        """
        检查是否为管理员
        
        Args:
            db: 数据库会话
            request: 请求对象
            
        Raises:
            BusinessException: 权限不足
        """
        user = await UserService.get_login_user(db, request)
        if user.user_role != "admin":
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限")

    @staticmethod
    async def add_user(db: AsyncSession, user_request: UserAddRequest) -> int:
        """
        添加用户（管理员）
        
        Args:
            db: 数据库会话
            user_request: 用户信息
            
        Returns:
            用户ID
        """
        result = await db.execute(
            select(User).where(User.user_account == user_request.user_account, User.is_delete == 0)
        )
        existing_user = result.scalar_one_or_none()
        if existing_user:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在")
        
        DEFAULT_PASSWORD = "12345678"
        encrypt_pwd = encrypt_password(DEFAULT_PASSWORD)
        
        new_user = User(
            user_account=user_request.user_account,
            user_password=encrypt_pwd,
            user_name=user_request.user_name or "无名",
            user_avatar=user_request.user_avatar,
            user_role=user_request.user_role or "user"
        )
        db.add(new_user)
        await db.commit()
        await db.refresh(new_user)
        
        return new_user.id

    @staticmethod
    async def get_user_by_id(db: AsyncSession, user_id: int) -> Dict[str, Any]:
        """
        根据ID获取用户详情
        
        Args:
            db: 数据库会话
            user_id: 用户ID
            
        Returns:
            用户详情
        """
        if user_id <= 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效")
        
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        
        if not user:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在")
        
        return {
            "id": user.id,
            "userAccount": user.user_account,
            "userName": user.user_name,
            "userAvatar": user.user_avatar,
            "userProfile": user.user_profile,
            "userRole": user.user_role,
            "createTime": user.create_time.isoformat() if user.create_time else None,
            "updateTime": user.update_time.isoformat() if user.update_time else None
        }

    @staticmethod
    async def get_user_vo_by_id(db: AsyncSession, user_id: int) -> UserVO:
        """
        根据ID获取用户VO（脱敏）
        
        Args:
            db: 数据库会话
            user_id: 用户ID
            
        Returns:
            用户VO
        """
        user_dict = await UserService.get_user_by_id(db, user_id)
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        return UserVO.model_validate(user)

    @staticmethod
    async def delete_user(db: AsyncSession, user_id: int) -> bool:
        """
        删除用户（逻辑删除）
        
        Args:
            db: 数据库会话
            user_id: 用户ID
            
        Returns:
            是否成功
        """
        if user_id <= 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效")
        
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        
        if not user:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在")
        
        user.is_delete = 1
        await db.commit()
        
        return True

    @staticmethod
    async def update_user(db: AsyncSession, user_request: UserUpdateRequest) -> bool:
        """
        更新用户信息
        
        Args:
            db: 数据库会话
            user_request: 更新请求
            
        Returns:
            是否成功
        """
        if user_request.id <= 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效")
        
        result = await db.execute(
            select(User).where(User.id == user_request.id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        
        if not user:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在")
        
        if user_request.user_name is not None:
            user.user_name = user_request.user_name
        if user_request.user_avatar is not None:
            user.user_avatar = user_request.user_avatar
        if user_request.user_profile is not None:
            user.user_profile = user_request.user_profile
        
        await db.commit()
        
        return True

    @staticmethod
    async def update_my_info(
        db: AsyncSession,
        user_id: int,
        user_name: Optional[str] = None,
        user_avatar: Optional[str] = None,
        user_profile: Optional[str] = None,
        daily_budget: Optional[Decimal] = None,
        monthly_budget: Optional[Decimal] = None,
        budget_alert_threshold: Optional[int] = None
    ) -> bool:
        """
        更新当前登录用户信息（含昵称、头像、简介、预算设置）
        """
        if user_id <= 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效")
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        if not user:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在")
        if user_name is not None:
            user.user_name = user_name
        if user_avatar is not None:
            user.user_avatar = user_avatar
        if user_profile is not None:
            user.user_profile = user_profile
        if daily_budget is not None:
            user.daily_budget = daily_budget
        if monthly_budget is not None:
            user.monthly_budget = monthly_budget
        if budget_alert_threshold is not None:
            user.budget_alert_threshold = budget_alert_threshold
        await db.commit()
        return True

    @staticmethod
    async def update_budget(
        db: AsyncSession,
        user_id: int,
        daily_budget: Optional[Decimal] = None,
        monthly_budget: Optional[Decimal] = None,
        alert_threshold: Optional[int] = None
    ) -> bool:
        """
        更新用户预算设置
        """
        if user_id <= 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效")
        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        if not user:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在")
        if daily_budget is not None:
            user.daily_budget = daily_budget
        if monthly_budget is not None:
            user.monthly_budget = monthly_budget
        if alert_threshold is not None:
            user.budget_alert_threshold = alert_threshold
        await db.commit()
        return True

    @staticmethod
    async def list_user_vo_by_page(
        db: AsyncSession, 
        query_request: UserQueryRequest
    ) -> Dict[str, Any]:
        """
        分页查询用户列表
        
        Args:
            db: 数据库会话
            query_request: 查询请求
            
        Returns:
            分页结果
        """
        query = select(User).where(User.is_delete == 0)
        
        if query_request.id:
            query = query.where(User.id == query_request.id)
        if query_request.user_account:
            query = query.where(User.user_account.like(f"%{query_request.user_account}%"))
        if query_request.user_name:
            query = query.where(User.user_name.like(f"%{query_request.user_name}%"))
        if query_request.user_role:
            query = query.where(User.user_role == query_request.user_role)
        
        count_query = select(func.count()).select_from(query.subquery())
        total_result = await db.execute(count_query)
        total = total_result.scalar()
        
        offset = (query_request.current - 1) * query_request.page_size
        query = query.offset(offset).limit(query_request.page_size)
        
        result = await db.execute(query)
        users = result.scalars().all()
        
        user_vo_list = [UserVO.model_validate(user) for user in users]
        
        return {
            "records": [vo.model_dump(by_alias=True) for vo in user_vo_list],
            "total": total,
            "current": query_request.current,
            "pageSize": query_request.page_size,
            "pages": (total + query_request.page_size - 1) // query_request.page_size
        }

    @staticmethod
    async def get_user_statistics(db: AsyncSession, user_id: int) -> UserStatisticsVO:
        """
        获取用户统计数据（总模型数、总Token、总成本、今日/本月成本、预算等）

        Args:
            db: 数据库会话
            user_id: 用户ID

        Returns:
            用户统计数据
        """
        today_start = datetime.combine(date.today(), datetime.min.time())
        first_day = date.today().replace(day=1)
        month_start = datetime.combine(first_day, datetime.min.time())
        if first_day.month == 12:
            next_month = first_day.replace(year=first_day.year + 1, month=1)
        else:
            next_month = first_day.replace(month=first_day.month + 1)
        month_end = datetime.combine(next_month, datetime.min.time())

        total_models_result = await db.execute(
            select(func.count()).select_from(Model).where(Model.is_delete == 0)
        )
        total_models = total_models_result.scalar() or 0

        cm_tokens = func.coalesce(ConversationMessage.input_tokens, 0) + func.coalesce(ConversationMessage.output_tokens, 0)
        tr_tokens = func.coalesce(TestResult.input_tokens, 0) + func.coalesce(TestResult.output_tokens, 0)

        cm_total = await db.execute(
            select(
                func.coalesce(func.sum(cm_tokens), 0).label("tokens"),
                func.coalesce(func.sum(ConversationMessage.cost), 0).label("cost")
            ).where(ConversationMessage.user_id == user_id, ConversationMessage.is_delete == 0)
        )
        cm_row = cm_total.one()
        cm_tokens_val = int(cm_row.tokens) if cm_row.tokens else 0
        cm_cost_val = Decimal(str(cm_row.cost)) if cm_row.cost else Decimal("0")

        tr_total = await db.execute(
            select(
                func.coalesce(func.sum(tr_tokens), 0).label("tokens"),
                func.coalesce(func.sum(TestResult.cost), 0).label("cost")
            ).where(TestResult.user_id == user_id, TestResult.is_delete == 0)
        )
        tr_row = tr_total.one()
        tr_tokens_val = int(tr_row.tokens) if tr_row.tokens else 0
        tr_cost_val = Decimal(str(tr_row.cost)) if tr_row.cost else Decimal("0")

        total_tokens = cm_tokens_val + tr_tokens_val
        total_cost = cm_cost_val + tr_cost_val

        cm_today = await db.execute(
            select(func.coalesce(func.sum(ConversationMessage.cost), 0)).where(
                ConversationMessage.user_id == user_id,
                ConversationMessage.is_delete == 0,
                ConversationMessage.create_time >= today_start
            )
        )
        today_cost_cm = Decimal(str(cm_today.scalar() or 0))

        tr_today = await db.execute(
            select(func.coalesce(func.sum(TestResult.cost), 0)).where(
                TestResult.user_id == user_id,
                TestResult.is_delete == 0,
                TestResult.create_time >= today_start
            )
        )
        today_cost_tr = Decimal(str(tr_today.scalar() or 0))
        today_cost = today_cost_cm + today_cost_tr

        cm_month = await db.execute(
            select(func.coalesce(func.sum(ConversationMessage.cost), 0)).where(
                ConversationMessage.user_id == user_id,
                ConversationMessage.is_delete == 0,
                ConversationMessage.create_time >= month_start,
                ConversationMessage.create_time < month_end
            )
        )
        month_cost_cm = Decimal(str(cm_month.scalar() or 0))

        tr_month = await db.execute(
            select(func.coalesce(func.sum(TestResult.cost), 0)).where(
                TestResult.user_id == user_id,
                TestResult.is_delete == 0,
                TestResult.create_time >= month_start,
                TestResult.create_time < month_end
            )
        )
        month_cost_tr = Decimal(str(tr_month.scalar() or 0))
        month_cost = month_cost_cm + month_cost_tr

        result = await db.execute(
            select(User).where(User.id == user_id, User.is_delete == 0)
        )
        user = result.scalar_one_or_none()
        daily_budget = user.daily_budget if user else None
        monthly_budget = user.monthly_budget if user else None
        alert_threshold = (user.budget_alert_threshold or 80) if user else 80

        daily_usage = Decimal("0")
        daily_alert = False
        monthly_usage = Decimal("0")
        monthly_alert = False
        if daily_budget and daily_budget > 0:
            daily_usage = (today_cost * 100 / daily_budget).quantize(Decimal("0.01"))
            daily_alert = daily_usage >= alert_threshold
        if monthly_budget and monthly_budget > 0:
            monthly_usage = (month_cost * 100 / monthly_budget).quantize(Decimal("0.01"))
            monthly_alert = monthly_usage >= alert_threshold

        return UserStatisticsVO(
            total_models=total_models,
            total_tokens=total_tokens,
            total_cost=total_cost,
            today_cost=today_cost,
            month_cost=month_cost,
            daily_budget=daily_budget,
            monthly_budget=monthly_budget,
            budget_alert_threshold=alert_threshold,
            daily_budget_usage_percent=daily_usage,
            monthly_budget_usage_percent=monthly_usage,
            daily_budget_alert=daily_alert,
            monthly_budget_alert=monthly_alert
        )
