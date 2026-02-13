"""
文件上传相关请求/响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional

from pydantic import BaseModel, Field


class Base64UploadFileRequest(BaseModel):
    """Base64 文件上传请求"""
    file_base64: str = Field(..., description="Base64 编码的文件内容", alias="fileBase64")
    biz: Optional[str] = Field(None, description="业务标识")

    class Config:
        populate_by_name = True


class UploadUrlFileRequest(BaseModel):
    """图片 URL 上传请求"""
    url: str = Field(..., description="图片地址")
    biz: Optional[str] = Field(None, description="业务标识")
    compress: bool = Field(False, description="是否压缩")


class UploadFileRequest(BaseModel):
    """表单上传时的可选参数（如 biz）"""
    biz: Optional[str] = Field(None, description="业务标识")


class UploadImageVO(BaseModel):
    """图片上传响应（与 Java UploadImageVO 一致）"""
    url: str = Field(..., description="可访问 URL")
    original_filename: Optional[str] = Field(None, description="原始文件名", alias="originalFilename")
    size: Optional[int] = Field(None, description="文件大小（字节）")
    content_type: Optional[str] = Field(None, description="Content-Type", alias="contentType")

    model_config = {"populate_by_name": True, "serialize_by_alias": True}
