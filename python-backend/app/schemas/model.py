"""
模型相关的 Pydantic 模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from pydantic import BaseModel, Field
from datetime import datetime
from decimal import Decimal


class ModelQueryRequest(BaseModel):
    """模型查询请求"""
    current: int = Field(default=1, description="当前页码", ge=1)
    page_size: int = Field(default=10, description="每页大小", ge=1, le=100, alias="pageSize")
    search_text: Optional[str] = Field(None, description="搜索关键词", alias="searchText")
    provider: Optional[str] = Field(None, description="提供商筛选")
    is_china: Optional[bool] = Field(None, description="是否国内模型", alias="isChina")
    recommended: Optional[bool] = Field(None, description="是否推荐")
    supports_multimodal: Optional[bool] = Field(None, description="是否支持多模态", alias="supportsMultimodal")
    supports_image_gen: Optional[bool] = Field(None, description="是否支持图片生成", alias="supportsImageGen")
    supports_tool_calling: Optional[bool] = Field(None, description="是否支持工具调用", alias="supportsToolCalling")

    class Config:
        populate_by_name = True


class ModelVO(BaseModel):
    """模型视图对象"""
    id: str
    name: str
    description: Optional[str]
    provider: Optional[str]
    context_length: Optional[int] = Field(None, alias="contextLength")
    input_price: Optional[Decimal] = Field(None, alias="inputPrice")
    output_price: Optional[Decimal] = Field(None, alias="outputPrice")
    recommended: bool
    is_china: bool = Field(..., alias="isChina")
    supports_multimodal: bool = Field(..., alias="supportsMultimodal")
    supports_image_gen: bool = Field(..., alias="supportsImageGen")
    supports_tool_calling: bool = Field(..., alias="supportsToolCalling")
    tags: Optional[str]
    total_tokens: int = Field(..., alias="totalTokens")
    total_cost: Decimal = Field(..., alias="totalCost")
    user_total_tokens: Optional[int] = Field(None, description="用户使用的总Token数", alias="userTotalTokens")
    user_total_cost: Optional[Decimal] = Field(None, description="用户的总花费", alias="userTotalCost")
    create_time: datetime = Field(..., alias="createTime")
    update_time: datetime = Field(..., alias="updateTime")

    model_config = {"from_attributes": True, "populate_by_name": True}


class OpenRouterModelData(BaseModel):
    """OpenRouter 模型数据"""
    id: str
    name: str
    description: Optional[str] = None
    pricing: Optional[dict] = None
    context_length: Optional[int] = None
    architecture: Optional[dict] = None
    top_provider: Optional[dict] = None
    per_request_limits: Optional[dict] = None


class OpenRouterModelResponse(BaseModel):
    """OpenRouter API 响应"""
    data: List[OpenRouterModelData]
