"""
对话相关的 Pydantic 模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime
from decimal import Decimal


class CreateConversationRequest(BaseModel):
    """创建对话请求"""
    conversation_type: str = Field(..., description="对话类型: side_by_side/prompt_lab/battle", alias="conversationType")
    models: List[str] = Field(..., description="参与的模型列表")
    title: Optional[str] = Field(None, description="对话标题")
    code_preview_enabled: Optional[bool] = Field(False, description="是否启用代码预览", alias="codePreviewEnabled")
    
    model_config = {"populate_by_name": True}


class ChatRequest(BaseModel):
    """基础对话请求"""
    conversation_id: Optional[str] = Field(None, description="对话ID（可选，如果为空则创建新对话）", alias="conversationId")
    model: str = Field(..., description="模型名称")
    message: str = Field(..., description="消息内容")
    image_urls: Optional[List[str]] = Field(None, description="图片URL列表", alias="imageUrls")
    web_search_enabled: Optional[bool] = Field(False, description="是否启用联网搜索", alias="webSearchEnabled")
    
    model_config = {"populate_by_name": True}


class SideBySideRequest(BaseModel):
    """Side-by-Side 多模型并排对比请求"""
    conversation_id: Optional[str] = Field(None, description="对话ID（可选）", alias="conversationId")
    models: List[str] = Field(..., description="模型列表（1-8个）")
    prompt: str = Field(..., description="提示词")
    image_urls: Optional[List[str]] = Field(None, description="图片URL列表", alias="imageUrls")
    web_search_enabled: Optional[bool] = Field(False, description="是否启用联网搜索", alias="webSearchEnabled")
    
    model_config = {"populate_by_name": True}


class PromptLabRequest(BaseModel):
    """Prompt Lab 单模型多提示词对比请求"""
    conversation_id: Optional[str] = Field(None, description="对话ID（可选）")
    model: str = Field(..., description="模型名称")
    prompt_variants: List[str] = Field(..., description="提示词变体列表")
    variant_image_urls: Optional[List[List[str]]] = Field(None, description="每个变体的图片URL列表")
    web_search_enabled: Optional[bool] = Field(False, description="是否启用联网搜索")


class BattleRequest(BaseModel):
    """Battle 匿名模型对比请求"""
    conversation_id: Optional[str] = Field(None, description="对话ID（可选）")
    models: Optional[List[str]] = Field(None, description="模型列表（可选，默认随机选择2个）")
    prompt: str = Field(..., description="提示词")
    image_urls: Optional[List[str]] = Field(None, description="图片URL列表")
    web_search_enabled: Optional[bool] = Field(False, description="是否启用联网搜索")
    code_preview_enabled: Optional[bool] = Field(False, description="是否启用代码预览")


class DeleteConversationRequest(BaseModel):
    """删除对话请求"""
    id: str = Field(..., description="对话ID")


class ConversationQueryRequest(BaseModel):
    """对话查询请求"""
    page_num: int = Field(1, description="当前页码", alias="pageNum", ge=1)
    page_size: int = Field(10, description="每页大小", alias="pageSize", ge=1, le=100)
    conversation_type: Optional[str] = Field(None, description="对话类型")
    
    model_config = {"populate_by_name": True}


class StreamChunkVO(BaseModel):
    """SSE 流式响应数据块"""
    conversation_id: Optional[str] = Field(None, description="对话ID", alias="conversationId")
    model_name: Optional[str] = Field(None, description="模型名称", alias="modelName")
    variant_index: Optional[int] = Field(None, description="变体索引", alias="variantIndex")
    content: Optional[str] = Field(None, description="内容片段")
    full_content: Optional[str] = Field(None, description="完整内容", alias="fullContent")
    input_tokens: Optional[int] = Field(None, description="输入Token数", alias="inputTokens")
    output_tokens: Optional[int] = Field(None, description="输出Token数", alias="outputTokens")
    total_tokens: Optional[int] = Field(None, description="总Token数", alias="totalTokens")
    elapsed_ms: Optional[int] = Field(None, description="已耗时（毫秒）", alias="elapsedMs")
    response_time_ms: Optional[int] = Field(None, description="响应时间（毫秒）", alias="responseTimeMs")
    cost: Optional[float] = Field(None, description="成本（USD）")
    done: Optional[bool] = Field(None, description="是否完成")
    error: Optional[str] = Field(None, description="错误信息")
    has_error: Optional[bool] = Field(None, description="是否发生错误", alias="hasError")
    reasoning: Optional[str] = Field(None, description="思考过程")
    has_reasoning: Optional[bool] = Field(None, description="是否有思考过程", alias="hasReasoning")
    thinking_time: Optional[int] = Field(None, description="思考时间（秒）", alias="thinkingTime")
    message_index: Optional[int] = Field(None, description="消息索引", alias="messageIndex")
    code_blocks: Optional[List[Dict[str, Any]]] = Field(None, description="代码块列表", alias="codeBlocks")
    has_code_blocks: Optional[bool] = Field(None, description="是否包含代码块", alias="hasCodeBlocks")
    tools_used: Optional[str] = Field(None, description="工具调用信息（JSON字符串）", alias="toolsUsed")
    budget_status: Optional[str] = Field(None, description="预算状态", alias="budgetStatus")
    budget_message: Optional[str] = Field(None, description="预算提示消息", alias="budgetMessage")
    today_cost: Optional[float] = Field(None, description="今日已消耗", alias="todayCost")
    daily_budget: Optional[float] = Field(None, description="日预算限额", alias="dailyBudget")
    daily_usage_percent: Optional[float] = Field(None, description="日预算使用百分比", alias="dailyUsagePercent")
    
    model_config = {"protected_namespaces": (), "populate_by_name": True}


class ConversationVO(BaseModel):
    """对话视图对象"""
    id: str
    user_id: int = Field(..., alias="userId")
    title: Optional[str]
    conversation_type: str = Field(..., alias="conversationType")
    models: List[str]
    code_preview_enabled: bool = Field(..., alias="codePreviewEnabled")
    is_anonymous: bool = Field(..., alias="isAnonymous")
    model_mapping: Optional[Dict[str, str]] = Field(None, alias="modelMapping")
    total_tokens: int = Field(..., alias="totalTokens")
    total_cost: Decimal = Field(..., alias="totalCost")
    create_time: datetime = Field(..., alias="createTime")
    update_time: datetime = Field(..., alias="updateTime")

    model_config = {"from_attributes": True, "protected_namespaces": (), "populate_by_name": True}


class ConversationMessageVO(BaseModel):
    """对话消息视图对象"""
    id: str
    conversation_id: str = Field(..., alias="conversationId")
    user_id: int = Field(..., alias="userId")
    message_index: int = Field(..., alias="messageIndex")
    role: str
    model_name: Optional[str] = Field(None, alias="modelName")
    variant_index: Optional[int] = Field(None, alias="variantIndex")
    content: str
    images: Optional[List[str]]
    tools_used: Optional[Dict[str, Any]] = Field(None, alias="toolsUsed")
    response_time_ms: Optional[int] = Field(None, alias="responseTimeMs")
    input_tokens: Optional[int] = Field(None, alias="inputTokens")
    output_tokens: Optional[int] = Field(None, alias="outputTokens")
    cost: Optional[Decimal]
    reasoning: Optional[str]
    code_blocks: Optional[str] = Field(None, alias="codeBlocks")
    create_time: datetime = Field(..., alias="createTime")

    model_config = {"from_attributes": True, "protected_namespaces": (), "populate_by_name": True}


class BattleModelMappingVO(BaseModel):
    """Battle模式模型映射关系"""
    model_mapping: Dict[str, str] = Field(..., description="模型映射关系，如 {'模型A': 'openai/gpt-4o'}")
    
    model_config = {"protected_namespaces": ()}
