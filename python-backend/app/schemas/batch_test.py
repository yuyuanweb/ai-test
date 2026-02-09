"""
批量测试相关请求/响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from pydantic import BaseModel, Field


class CreateBatchTestRequest(BaseModel):
    """
    创建批量测试任务请求
    """
    name: Optional[str] = Field(None, description="任务名称")
    scene_id: str = Field(..., description="场景ID", alias="sceneId")
    models: List[str] = Field(..., description="测试的模型列表")
    temperature: Optional[float] = Field(None, description="温度参数 (0.0-2.0)")
    top_p: Optional[float] = Field(None, description="Top P 参数", alias="topP")
    max_tokens: Optional[int] = Field(None, description="最大Token数", alias="maxTokens")
    top_k: Optional[int] = Field(None, description="Top K 参数", alias="topK")
    frequency_penalty: Optional[float] = Field(None, description="Frequency Penalty", alias="frequencyPenalty")
    presence_penalty: Optional[float] = Field(None, description="Presence Penalty", alias="presencePenalty")
    enable_ai_scoring: Optional[bool] = Field(None, description="是否启用AI评分", alias="enableAiScoring")

    class Config:
        populate_by_name = True


class TaskQueryRequest(BaseModel):
    """
    任务查询请求
    """
    page_num: int = Field(default=1, description="页码", alias="pageNum")
    page_size: int = Field(default=10, description="每页大小", alias="pageSize")
    status: Optional[str] = Field(None, description="状态筛选")
    keyword: Optional[str] = Field(None, description="关键词搜索")
    category: Optional[str] = Field(None, description="分类筛选")
    start_time: Optional[str] = Field(None, description="开始时间", alias="startTime")
    end_time: Optional[str] = Field(None, description="结束时间", alias="endTime")

    class Config:
        populate_by_name = True


class UpdateTestResultRatingRequest(BaseModel):
    """
    更新测试结果评分请求
    """
    result_id: str = Field(..., description="测试结果ID", alias="resultId")
    user_rating: Optional[int] = Field(None, description="用户评分(1-5)", alias="userRating")

    class Config:
        populate_by_name = True


class BatchTestDeleteRequest(BaseModel):
    """
    删除批量测试任务请求
    """
    id: str = Field(..., description="任务ID")
