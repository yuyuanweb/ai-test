"""
场景相关请求/响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from datetime import datetime
from pydantic import BaseModel, Field


class CreateSceneRequest(BaseModel):
    """
    创建场景请求
    """
    name: str = Field(..., description="场景名称")
    description: Optional[str] = Field(None, description="场景描述")
    category: Optional[str] = Field(None, description="分类:编程/数学/文案等")


class UpdateSceneRequest(BaseModel):
    """
    更新场景请求
    """
    id: str = Field(..., description="场景ID")
    name: Optional[str] = Field(None, description="场景名称")
    description: Optional[str] = Field(None, description="场景描述")
    category: Optional[str] = Field(None, description="分类")
    is_active: Optional[int] = Field(None, description="是否启用(1-启用 0-禁用)", alias="isActive")

    class Config:
        populate_by_name = True


class SceneDeleteRequest(BaseModel):
    """
    删除场景/提示词请求（ID为字符串UUID）
    """
    id: str = Field(..., description="要删除的记录ID")


class AddScenePromptRequest(BaseModel):
    """
    添加场景提示词请求
    """
    scene_id: str = Field(..., description="场景ID", alias="sceneId")
    title: str = Field(..., description="提示词标题")
    content: str = Field(..., description="提示词内容")
    difficulty: Optional[str] = Field(None, description="难度: easy/medium/hard")
    expected_output: Optional[str] = Field(None, description="期望输出", alias="expectedOutput")

    model_config = {"populate_by_name": True}


class UpdateScenePromptRequest(BaseModel):
    """
    更新场景提示词请求
    """
    id: str = Field(..., description="提示词ID")
    title: Optional[str] = Field(None, description="提示词标题")
    content: Optional[str] = Field(None, description="提示词内容")
    difficulty: Optional[str] = Field(None, description="难度: easy/medium/hard")
    expected_output: Optional[str] = Field(None, description="期望输出", alias="expectedOutput")

    model_config = {"populate_by_name": True}


class ScenePromptVO(BaseModel):
    """
    场景提示词视图对象
    """
    id: str
    scene_id: str = Field(..., alias="sceneId")
    user_id: int = Field(..., alias="userId")
    prompt_index: int = Field(..., alias="promptIndex")
    title: str
    content: str
    difficulty: Optional[str] = None
    tags: Optional[str] = None
    expected_output: Optional[str] = Field(None, alias="expectedOutput")
    create_time: Optional[datetime] = Field(None, alias="createTime")
    update_time: Optional[datetime] = Field(None, alias="updateTime")

    model_config = {"populate_by_name": True, "from_attributes": True}


class AddScenePromptRequest(BaseModel):
    """
    添加场景提示词请求
    """
    scene_id: str = Field(..., description="场景ID", alias="sceneId")
    title: str = Field(..., description="提示词标题")
    content: str = Field(..., description="提示词内容")
    difficulty: Optional[str] = Field(None, description="难度: easy/medium/hard")
    expected_output: Optional[str] = Field(None, description="期望输出", alias="expectedOutput")

    model_config = {"populate_by_name": True}


class UpdateScenePromptRequest(BaseModel):
    """
    更新场景提示词请求
    """
    id: str = Field(..., description="提示词ID")
    title: Optional[str] = Field(None, description="提示词标题")
    content: Optional[str] = Field(None, description="提示词内容")
    difficulty: Optional[str] = Field(None, description="难度: easy/medium/hard")
    expected_output: Optional[str] = Field(None, description="期望输出", alias="expectedOutput")

    model_config = {"populate_by_name": True}


class ScenePromptVO(BaseModel):
    """
    场景提示词视图对象
    """
    id: str
    scene_id: str = Field(..., alias="sceneId")
    user_id: int = Field(..., alias="userId")
    prompt_index: int = Field(..., alias="promptIndex")
    title: str
    content: str
    difficulty: Optional[str] = None
    tags: Optional[str] = None
    expected_output: Optional[str] = Field(None, alias="expectedOutput")
    create_time: Optional[datetime] = Field(None, alias="createTime")
    update_time: Optional[datetime] = Field(None, alias="updateTime")

    model_config = {"populate_by_name": True, "from_attributes": True}


class SceneVO(BaseModel):
    """
    场景视图对象
    """
    id: str
    user_id: Optional[int] = Field(None, alias="userId")
    name: str
    description: Optional[str] = None
    category: Optional[str] = None
    is_preset: Optional[int] = Field(None, alias="isPreset")
    is_active: Optional[int] = Field(None, alias="isActive")
    create_time: Optional[datetime] = Field(None, alias="createTime")
    update_time: Optional[datetime] = Field(None, alias="updateTime")

    class Config:
        populate_by_name = True
        from_attributes = True
