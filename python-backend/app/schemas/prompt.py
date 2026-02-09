"""
提示词模板与优化相关 Schema
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import List, Optional
from pydantic import BaseModel, Field


class CreatePromptTemplateRequest(BaseModel):
    """
    创建提示词模板请求
    """
    name: str = Field(..., description="模板名称")
    description: Optional[str] = Field(default=None, description="模板描述")
    strategy: str = Field(..., description="策略类型: direct/cot/role_play/few_shot")
    content: str = Field(..., description="模板内容")
    variables: Optional[List[str]] = Field(default=None, description="变量列表")
    category: Optional[str] = Field(default=None, description="分类")


class UpdatePromptTemplateRequest(BaseModel):
    """
    更新提示词模板请求
    """
    id: str = Field(..., description="模板ID")
    name: Optional[str] = Field(default=None, description="模板名称")
    description: Optional[str] = Field(default=None, description="模板描述")
    strategy: Optional[str] = Field(default=None, description="策略类型: direct/cot/role_play/few_shot")
    content: Optional[str] = Field(default=None, description="模板内容")
    variables: Optional[List[str]] = Field(default=None, description="变量列表")
    category: Optional[str] = Field(default=None, description="分类")
    is_active: Optional[bool] = Field(default=None, description="是否启用", alias="isActive")

    class Config:
        populate_by_name = True


class PromptTemplateDeleteRequest(BaseModel):
    """
    删除提示词模板请求（ID 为字符串 UUID）
    """
    id: str = Field(..., description="要删除的模板ID")


class PromptTemplateVO(BaseModel):
    """
    提示词模板视图对象
    """
    id: str = Field(..., description="模板ID")
    name: str = Field(..., description="模板名称")
    description: Optional[str] = Field(default=None, description="模板描述")
    strategy: str = Field(..., description="策略类型: direct/cot/role_play/few_shot")
    strategy_name: str = Field(..., description="策略类型显示名称", alias="strategyName")
    content: str = Field(..., description="模板内容")
    variables: List[str] = Field(default_factory=list, description="变量列表")
    category: Optional[str] = Field(default=None, description="分类")
    is_preset: bool = Field(..., description="是否为预设模板", alias="isPreset")
    usage_count: int = Field(default=0, description="使用次数", alias="usageCount")
    is_active: bool = Field(..., description="是否启用", alias="isActive")
    create_time: Optional[str] = Field(default=None, description="创建时间", alias="createTime")

    class Config:
        populate_by_name = True


class PromptOptimizationRequest(BaseModel):
    """
    提示词优化请求
    """
    original_prompt: str = Field(..., description="原始提示词", alias="originalPrompt")
    ai_response: Optional[str] = Field(default=None, description="AI回答（可选，用于更精准的分析）", alias="aiResponse")
    evaluation_model: Optional[str] = Field(default=None, description="评估模型（可选）", alias="evaluationModel")

    class Config:
        populate_by_name = True


class PromptOptimizationVO(BaseModel):
    """
    提示词优化结果视图对象
    """
    issues: List[str] = Field(default_factory=list, description="当前提示词存在的问题列表")
    optimized_prompt: str = Field(default="", description="优化后的完整提示词", alias="optimizedPrompt")
    improvements: List[str] = Field(default_factory=list, description="改进点列表", alias="improvements")

    class Config:
        populate_by_name = True
