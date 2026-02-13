"""
图片生成相关请求与响应模型（与 Java ImageController 一致）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from pydantic import BaseModel, Field
from decimal import Decimal


class GenerateImageRequest(BaseModel):
    """图片生成请求（与 Java GenerateImageRequest 一致）"""
    model: Optional[str] = Field(None, description="模型名称（OpenRouter 模型 ID）")
    is_anonymous: Optional[bool] = Field(None, description="是否匿名模式（Battle）", alias="isAnonymous")
    prompt: str = Field(..., min_length=1, description="图片生成提示词")
    reference_image_urls: Optional[List[str]] = Field(None, description="参考图片 URL 列表（图生图）", alias="referenceImageUrls")
    count: int = Field(..., ge=1, le=4, description="生成数量（1-4）")
    conversation_id: Optional[str] = Field(None, description="对话 ID（可选）", alias="conversationId")
    models: Optional[List[str]] = Field(None, description="模型列表（创建会话时）")
    conversation_type: Optional[str] = Field(None, description="会话类型 side_by_side/prompt_lab", alias="conversationType")
    variant_index: Optional[int] = Field(None, description="变体索引", alias="variantIndex")
    message_index: Optional[int] = Field(None, description="消息索引", alias="messageIndex")

    class Config:
        populate_by_name = True


class GeneratedImageVO(BaseModel):
    """生成图片结果 VO（与 Java GeneratedImageVO 一致）"""
    model_config = {"protected_namespaces": (), "populate_by_name": True}

    url: str = Field(..., description="图片访问地址")
    model_name: Optional[str] = Field(None, alias="modelName")
    index: Optional[int] = Field(None, description="生成序号（从 0 开始）")
    input_tokens: Optional[int] = Field(None, alias="inputTokens")
    output_tokens: Optional[int] = Field(None, alias="outputTokens")
    total_tokens: Optional[int] = Field(None, alias="totalTokens")
    cost: Optional[float] = Field(None, description="本次调用费用（USD）")
    conversation_id: Optional[str] = Field(None, alias="conversationId")
    message_index: Optional[int] = Field(None, alias="messageIndex")


class ImageStreamChunkVO(BaseModel):
    """图像生成流式响应块（与 Java ImageStreamChunkVO 一致）"""
    model_config = {"protected_namespaces": (), "populate_by_name": True}

    type: Optional[str] = Field(None, description="事件类型：thinking/image/done/error")
    thinking: Optional[str] = Field(None, description="思考内容")
    full_thinking: Optional[str] = Field(None, alias="fullThinking")
    image: Optional[GeneratedImageVO] = Field(None, description="生成的图片信息")
    images: Optional[List[GeneratedImageVO]] = Field(None, description="done 时返回的完整图片列表")
    conversation_id: Optional[str] = Field(None, alias="conversationId")
    message_index: Optional[int] = Field(None, alias="messageIndex")
    variant_index: Optional[int] = Field(None, alias="variantIndex")
    model_name: Optional[str] = Field(None, alias="modelName")
    error: Optional[str] = Field(None, description="错误信息")
