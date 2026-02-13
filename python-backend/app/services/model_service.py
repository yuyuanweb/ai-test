"""
模型服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import httpx
from typing import List, Optional
from decimal import Decimal
from sqlalchemy import select, and_, or_, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.model import Model
from app.schemas.model import ModelVO, ModelQueryRequest, OpenRouterModelResponse
from app.core.config import get_settings
from app.core.errors import BusinessException, ErrorCode

settings = get_settings()


class ModelService:
    """
    模型服务类
    """
    
    def __init__(self, db: AsyncSession):
        self.db = db
    
    async def list_models(
        self,
        query_request: ModelQueryRequest,
        user_id: Optional[int] = None
    ) -> tuple[List[ModelVO], int]:
        """
        分页查询模型列表
        
        Args:
            query_request: 查询请求
            user_id: 用户ID（可选）
            
        Returns:
            (模型列表, 总数)
        """
        query = select(Model).where(Model.is_delete == 0)
        
        if query_request.search_text:
            search_pattern = f"%{query_request.search_text}%"
            query = query.where(
                or_(
                    Model.name.like(search_pattern),
                    Model.id.like(search_pattern),
                    Model.description.like(search_pattern)
                )
            )
        
        if query_request.provider:
            query = query.where(Model.provider == query_request.provider)
        
        if query_request.only_china is True:
            query = query.where(Model.is_china == 1)
        if query_request.only_recommended is True:
            query = query.where(Model.recommended == 1)
        if query_request.only_supports_multimodal is True:
            query = query.where(Model.supports_multimodal == 1)
        if query_request.only_supports_image_gen is True:
            query = query.where(Model.supports_image_gen == 1)
        
        count_result = await self.db.execute(
            select(func.count()).select_from(query.subquery())
        )
        total = count_result.scalar()
        
        query = query.order_by(
            Model.is_china.desc(),
            Model.recommended.desc(),
            Model.update_time.desc()
        )
        
        current = query_request.page_num if query_request.page_num is not None else query_request.current
        offset = (current - 1) * query_request.page_size
        query = query.offset(offset).limit(query_request.page_size)
        
        result = await self.db.execute(query)
        models = result.scalars().all()
        
        model_vos = [ModelVO.model_validate(model) for model in models]
        
        return model_vos, total
    
    async def get_all_models(self, user_id: Optional[int] = None) -> List[ModelVO]:
        """
        获取所有模型列表（国内优先）
        
        Args:
            user_id: 用户ID（可选）
            
        Returns:
            模型列表
        """
        result = await self.db.execute(
            select(Model)
            .where(Model.is_delete == 0)
            .order_by(
                Model.is_china.desc(),
                Model.recommended.desc(),
                Model.update_time.desc()
            )
        )
        models = result.scalars().all()
        
        return [ModelVO.model_validate(model) for model in models]

    async def get_model_by_id(self, model_id: str):
        """根据模型 ID 查询单条（未删除）。"""
        if not model_id or not model_id.strip():
            return None
        result = await self.db.execute(
            select(Model).where(Model.id == model_id, Model.is_delete == 0)
        )
        return result.scalar_one_or_none()
    
    async def sync_models_from_openrouter(self) -> int:
        """
        从 OpenRouter 同步模型列表
        
        Returns:
            同步的模型数量
        """
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    f"{settings.OPENROUTER_BASE_URL}/models",
                    headers={
                        "Authorization": f"Bearer {settings.OPENROUTER_API_KEY}",
                        "HTTP-Referer": "https://codefather.cn",
                        "X-Title": "AI Evaluation Platform"
                    },
                    timeout=30.0
                )
                response.raise_for_status()
                
                data = response.json()
                models_data = data.get("data", [])
                
                synced_count = 0
                
                for model_data in models_data:
                    model_id = model_data.get("id")
                    if not model_id:
                        continue
                    
                    result = await self.db.execute(
                        select(Model).where(Model.id == model_id)
                    )
                    existing_model = result.scalar_one_or_none()
                    
                    pricing = model_data.get("pricing", {})
                    input_price = None
                    output_price = None
                    
                    if pricing:
                        prompt_price = pricing.get("prompt")
                        completion_price = pricing.get("completion")
                        
                        if prompt_price:
                            input_price = Decimal(str(prompt_price)) * Decimal('1000000')
                        if completion_price:
                            output_price = Decimal(str(completion_price)) * Decimal('1000000')
                    
                    architecture = model_data.get("architecture", {})
                    modality = architecture.get("modality", "text")
                    
                    supports_multimodal = 1 if "image" in modality or "multimodal" in modality else 0
                    supports_image_gen = 1 if "image" in modality and "output" in str(architecture) else 0
                    supports_tool_calling = 1 if model_data.get("supported_generation_methods") and \
                        "tools" in str(model_data.get("supported_generation_methods")) else 0
                    
                    is_china = self._is_china_model(model_id)
                    
                    if existing_model:
                        existing_model.name = model_data.get("name", model_id)
                        existing_model.description = model_data.get("description")
                        existing_model.context_length = model_data.get("context_length")
                        existing_model.input_price = input_price
                        existing_model.output_price = output_price
                        existing_model.supports_multimodal = supports_multimodal
                        existing_model.supports_image_gen = supports_image_gen
                        existing_model.supports_tool_calling = supports_tool_calling
                        existing_model.raw_data = json.dumps(model_data)
                        existing_model.is_china = is_china
                    else:
                        new_model = Model(
                            id=model_id,
                            name=model_data.get("name", model_id),
                            description=model_data.get("description"),
                            provider=self._extract_provider(model_id),
                            context_length=model_data.get("context_length"),
                            input_price=input_price,
                            output_price=output_price,
                            recommended=0,
                            is_china=is_china,
                            supports_multimodal=supports_multimodal,
                            supports_image_gen=supports_image_gen,
                            supports_tool_calling=supports_tool_calling,
                            raw_data=json.dumps(model_data),
                            total_tokens=0,
                            total_cost=Decimal('0'),
                            is_delete=0
                        )
                        self.db.add(new_model)
                    
                    synced_count += 1
                
                await self.db.commit()
                return synced_count
                
        except Exception as e:
            raise BusinessException(ErrorCode.SYSTEM_ERROR, f"同步模型失败: {str(e)}")
    
    def _extract_provider(self, model_id: str) -> str:
        """
        从模型ID提取提供商
        """
        if "/" in model_id:
            return model_id.split("/")[0]
        return "unknown"
    
    def _is_china_model(self, model_id: str) -> int:
        """
        判断是否为国内模型
        """
        china_providers = [
            "qwen", "alibaba", "baidu", "tencent", 
            "zhipu", "deepseek", "moonshot", "bytedance"
        ]
        
        model_id_lower = model_id.lower()
        
        for provider in china_providers:
            if provider in model_id_lower:
                return 1
        
        return 0
