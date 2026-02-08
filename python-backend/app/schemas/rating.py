"""
评分相关的 Pydantic 模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional
from pydantic import BaseModel, Field
from datetime import datetime


class RatingAddRequest(BaseModel):
    """添加或更新评分请求"""
    conversation_id: str = Field(..., description="对话ID", alias="conversationId")
    message_index: int = Field(..., description="消息序号", alias="messageIndex")
    rating_type: str = Field(..., description="评分类型: left_better/right_better/tie/both_bad/variant_N", alias="ratingType")
    winner_model: Optional[str] = Field(None, description="获胜模型", alias="winnerModel")
    loser_model: Optional[str] = Field(None, description="失败模型", alias="loserModel")
    winner_variant_index: Optional[int] = Field(None, description="获胜变体索引(用于prompt_lab)", alias="winnerVariantIndex")
    loser_variant_index: Optional[int] = Field(None, description="失败变体索引(用于prompt_lab)", alias="loserVariantIndex")
    
    model_config = {"populate_by_name": True}


class RatingVO(BaseModel):
    """评分视图对象"""
    id: str
    conversation_id: str = Field(..., alias="conversationId")
    message_index: int = Field(..., alias="messageIndex")
    user_id: int = Field(..., alias="userId")
    rating_type: str = Field(..., alias="ratingType")
    winner_model: Optional[str] = Field(None, alias="winnerModel")
    loser_model: Optional[str] = Field(None, alias="loserModel")
    winner_variant_index: Optional[int] = Field(None, alias="winnerVariantIndex")
    loser_variant_index: Optional[int] = Field(None, alias="loserVariantIndex")
    create_time: datetime = Field(..., alias="createTime")
    update_time: datetime = Field(..., alias="updateTime")

    model_config = {"from_attributes": True, "populate_by_name": True}
