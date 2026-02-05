"""
对话相关的请求和响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """
    对话请求
    """
    message: str = Field(..., min_length=1, description="用户消息")
    model: str = Field(default="deepseek/deepseek-chat", description="模型名称")
    
    class Config:
        json_schema_extra = {
            "example": {
                "message": "你好，请介绍一下你自己",
                "model": "deepseek/deepseek-chat"
            }
        }


class ChatResponse(BaseModel):
    """
    对话响应
    """
    content: str = Field(..., description="模型回复内容")
    model: str = Field(..., description="使用的模型名称")
    
    class Config:
        json_schema_extra = {
            "example": {
                "content": "你好！我是一个AI助手...",
                "model": "deepseek/deepseek-chat"
            }
        }


class StreamChunkVO(BaseModel):
    """
    流式响应数据块VO
    """
    conversationId: Optional[str] = Field(None, description="对话ID")
    modelName: Optional[str] = Field(None, description="模型名称")
    variantIndex: Optional[int] = Field(None, description="变体索引")
    content: Optional[str] = Field(None, description="内容片段")
    fullContent: Optional[str] = Field(None, description="完整内容")
    inputTokens: Optional[int] = Field(None, description="输入Token数")
    outputTokens: Optional[int] = Field(None, description="输出Token数")
    totalTokens: Optional[int] = Field(None, description="总Token数")
    elapsedMs: Optional[int] = Field(None, description="已耗时（毫秒）")
    responseTimeMs: Optional[int] = Field(None, description="响应时间（毫秒）")
    cost: Optional[float] = Field(None, description="成本（USD）")
    done: Optional[bool] = Field(False, description="是否完成")
    error: Optional[str] = Field(None, description="错误信息")
    hasError: Optional[bool] = Field(False, description="是否发生错误")
    reasoning: Optional[str] = Field(None, description="思考过程")
    hasReasoning: Optional[bool] = Field(False, description="是否有思考过程")
    thinkingTime: Optional[int] = Field(None, description="思考时间（秒）")
    messageIndex: Optional[int] = Field(None, description="消息索引")
    codeBlocks: Optional[List[dict]] = Field(None, description="代码块列表")
    hasCodeBlocks: Optional[bool] = Field(False, description="是否包含代码块")
    toolsUsed: Optional[str] = Field(None, description="工具调用信息")
    budgetStatus: Optional[str] = Field(None, description="预算状态")
    budgetMessage: Optional[str] = Field(None, description="预算提示消息")
    todayCost: Optional[float] = Field(None, description="今日已消耗")
    dailyBudget: Optional[float] = Field(None, description="日预算限额")
    dailyUsagePercent: Optional[float] = Field(None, description="日预算使用百分比")
