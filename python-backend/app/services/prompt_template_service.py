"""
提示词模板服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import uuid
from datetime import datetime
from typing import List, Optional

from sqlalchemy import select, or_, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import BusinessException, ErrorCode
from app.core.logging_config import logger
from app.models.prompt_template import PromptTemplate
from app.schemas.prompt import PromptTemplateVO

STRATEGY_NAME_MAP = {
    "direct": "直接提问",
    "cot": "CoT (思维链)",
    "role_play": "角色扮演",
    "few_shot": "Few-shot (示例学习)",
}


def _to_vo(template: PromptTemplate) -> PromptTemplateVO:
    """
    实体转 VO
    """
    variables: List[str] = []
    if template.variables and template.variables.strip():
        try:
            variables = json.loads(template.variables)
            if not isinstance(variables, list):
                variables = []
        except (json.JSONDecodeError, TypeError) as e:
            logger.warning("解析模板变量失败: %s", e)
    create_time_str = None
    if template.create_time:
        create_time_str = template.create_time.isoformat()
    return PromptTemplateVO(
        id=template.id,
        name=template.name,
        description=template.description,
        strategy=template.strategy,
        strategyName=STRATEGY_NAME_MAP.get(template.strategy, template.strategy),
        content=template.content,
        variables=variables,
        category=template.category,
        isPreset=(template.is_preset == 1),
        usageCount=template.usage_count or 0,
        isActive=(template.is_active == 1),
        createTime=create_time_str,
    )


class PromptTemplateService:
    """
    提示词模板服务类
    """

    @staticmethod
    async def list_templates(
        db: AsyncSession,
        user_id: int,
        strategy: Optional[str] = None,
    ) -> List[PromptTemplateVO]:
        """
        获取所有模板（预设 + 用户自定义）

        Args:
            db: 数据库会话
            user_id: 用户ID
            strategy: 策略类型（可选）

        Returns:
            模板列表
        """
        conditions = [
            PromptTemplate.is_delete == 0,
            PromptTemplate.is_active == 1,
            or_(PromptTemplate.is_preset == 1, PromptTemplate.user_id == user_id),
        ]
        if strategy and strategy.strip():
            conditions.append(PromptTemplate.strategy == strategy.strip())

        result = await db.execute(
            select(PromptTemplate)
            .where(and_(*conditions))
            .order_by(PromptTemplate.is_preset.desc(), PromptTemplate.usage_count.desc(), PromptTemplate.create_time.desc())
        )
        templates = result.scalars().all()
        return [_to_vo(t) for t in templates]

    @staticmethod
    async def get_template_by_id(
        db: AsyncSession,
        template_id: str,
        user_id: int,
    ) -> PromptTemplateVO:
        """
        根据 ID 获取模板

        Args:
            db: 数据库会话
            template_id: 模板ID
            user_id: 用户ID

        Returns:
            模板 VO

        Raises:
            BusinessException: 业务异常
        """
        if not template_id or not template_id.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空")

        result = await db.execute(
            select(PromptTemplate).where(PromptTemplate.id == template_id.strip(), PromptTemplate.is_delete == 0)
        )
        template = result.scalar_one_or_none()
        if not template:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在")

        if template.is_preset == 0 and template.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问")

        return _to_vo(template)

    @staticmethod
    async def create_template(
        db: AsyncSession,
        name: str,
        strategy: str,
        content: str,
        user_id: int,
        description: Optional[str] = None,
        variables: Optional[List[str]] = None,
        category: Optional[str] = None,
    ) -> str:
        """
        创建模板

        Args:
            db: 数据库会话
            name: 模板名称
            strategy: 策略类型
            content: 模板内容
            user_id: 用户ID
            description: 描述
            variables: 变量列表
            category: 分类

        Returns:
            模板ID

        Raises:
            BusinessException: 业务异常
        """
        if not name or not name.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模板名称不能为空")
        if not strategy or not strategy.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "策略类型不能为空")
        if not content or not content.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模板内容不能为空")

        template_id = str(uuid.uuid4())
        variables_json = json.dumps(variables, ensure_ascii=False) if variables else None

        template = PromptTemplate(
            id=template_id,
            user_id=user_id,
            name=name.strip(),
            description=description.strip() if description and description.strip() else None,
            strategy=strategy.strip(),
            content=content.strip(),
            variables=variables_json,
            category=category.strip() if category and category.strip() else None,
            is_preset=0,
            usage_count=0,
            is_active=1,
            is_delete=0,
        )
        db.add(template)
        await db.commit()
        return template_id

    @staticmethod
    async def update_template(
        db: AsyncSession,
        template_id: str,
        user_id: int,
        name: Optional[str] = None,
        description: Optional[str] = None,
        strategy: Optional[str] = None,
        content: Optional[str] = None,
        variables: Optional[List[str]] = None,
        category: Optional[str] = None,
        is_active: Optional[bool] = None,
    ) -> bool:
        """
        更新模板

        Args:
            db: 数据库会话
            template_id: 模板ID
            user_id: 用户ID
            name: 模板名称
            description: 描述
            strategy: 策略类型
            content: 模板内容
            variables: 变量列表
            category: 分类
            is_active: 是否启用

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        if not template_id or not template_id.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空")

        result = await db.execute(
            select(PromptTemplate).where(PromptTemplate.id == template_id.strip(), PromptTemplate.is_delete == 0)
        )
        existing = result.scalar_one_or_none()
        if not existing:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在")

        if existing.is_preset == 1:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "预设模板不能修改")

        if existing.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        if name is not None:
            existing.name = name.strip()
        if description is not None:
            existing.description = (description.strip() or None) if isinstance(description, str) else description
        if strategy is not None:
            existing.strategy = strategy.strip()
        if content is not None:
            existing.content = content.strip()
        if variables is not None:
            existing.variables = json.dumps(variables, ensure_ascii=False) if variables else None
        if category is not None:
            existing.category = category.strip() if category.strip() else None
        if is_active is not None:
            existing.is_active = 1 if is_active else 0
        existing.update_time = datetime.now()
        await db.commit()
        return True

    @staticmethod
    async def delete_template(
        db: AsyncSession,
        template_id: str,
        user_id: int,
    ) -> bool:
        """
        删除模板（逻辑删除）

        Args:
            db: 数据库会话
            template_id: 模板ID
            user_id: 用户ID

        Returns:
            是否成功

        Raises:
            BusinessException: 业务异常
        """
        if not template_id or not template_id.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空")

        result = await db.execute(
            select(PromptTemplate).where(PromptTemplate.id == template_id.strip(), PromptTemplate.is_delete == 0)
        )
        template = result.scalar_one_or_none()
        if not template:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在")

        if template.is_preset == 1:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "预设模板不能删除")

        if template.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作")

        template.is_delete = 1
        await db.commit()
        return True

    @staticmethod
    async def increment_usage_count(db: AsyncSession, template_id: str) -> bool:
        """
        增加使用次数

        Args:
            db: 数据库会话
            template_id: 模板ID

        Returns:
            是否成功
        """
        if not template_id or not template_id.strip():
            return False

        result = await db.execute(
            select(PromptTemplate).where(PromptTemplate.id == template_id.strip(), PromptTemplate.is_delete == 0)
        )
        template = result.scalar_one_or_none()
        if not template:
            return False

        template.usage_count = (template.usage_count or 0) + 1
        template.update_time = datetime.now()
        await db.commit()
        return True
