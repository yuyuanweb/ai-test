"""
腾讯云 COS 对象存储客户端封装
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import io
import logging
from pathlib import Path
from typing import BinaryIO

from qcloud_cos import CosConfig, CosS3Client

from app.core.config import get_settings

logger = logging.getLogger(__name__)


def _get_client() -> CosS3Client:
    settings = get_settings()
    config = CosConfig(
        Region=settings.TENCENT_COS_REGION,
        SecretId=settings.TENCENT_COS_ACCESS_KEY,
        SecretKey=settings.TENCENT_COS_SECRET_KEY,
        Scheme="https",
    )
    return CosS3Client(config)


def put_object(key: str, body: bytes | BinaryIO | Path) -> None:
    """
    上传对象到 COS。
    key 为对象键，如 aitest/1/images/xxx.png。
    body 为文件内容（字节流、文件对象或本地路径）。
    """
    settings = get_settings()
    if not settings.TENCENT_COS_BUCKET:
        raise ValueError("TENCENT_COS_BUCKET 未配置")
    client = _get_client()
    key_stripped = key.lstrip("/")
    if isinstance(body, Path):
        with open(body, "rb") as f:
            client.put_object(Bucket=settings.TENCENT_COS_BUCKET, Key=key_stripped, Body=f)
    elif isinstance(body, bytes):
        client.put_object(Bucket=settings.TENCENT_COS_BUCKET, Key=key_stripped, Body=io.BytesIO(body))
    else:
        client.put_object(Bucket=settings.TENCENT_COS_BUCKET, Key=key_stripped, Body=body)
    logger.info("COS 上传成功: key=%s", key)
