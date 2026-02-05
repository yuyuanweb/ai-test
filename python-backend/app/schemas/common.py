"""
通用响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Generic, TypeVar, Optional
from pydantic import BaseModel, Field

T = TypeVar('T')


class BaseResponse(BaseModel, Generic[T]):
    """
    通用响应包装类
    """
    code: int = Field(default=0, description="响应码，0表示成功")
    data: Optional[T] = Field(default=None, description="响应数据")
    message: str = Field(default="ok", description="响应消息")

    class Config:
        json_schema_extra = {
            "example": {
                "code": 0,
                "data": None,
                "message": "ok"
            }
        }


class DeleteRequest(BaseModel):
    """
    删除请求
    """
    id: int = Field(..., description="要删除的记录ID", gt=0)


class PageRequest(BaseModel):
    """
    分页请求
    """
    current: int = Field(default=1, description="当前页码", ge=1)
    page_size: int = Field(default=10, description="每页大小", ge=1, le=100, alias="pageSize")
    sort_field: Optional[str] = Field(default=None, description="排序字段", alias="sortField")
    sort_order: Optional[str] = Field(default="descend", description="排序顺序", alias="sortOrder")

    class Config:
        populate_by_name = True
