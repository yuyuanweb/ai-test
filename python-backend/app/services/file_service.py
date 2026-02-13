"""
文件上传服务（腾讯云 COS）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import base64
import re
import secrets
import tempfile
from pathlib import Path

import httpx

from app.core.config import get_settings
from app.core.errors import BusinessException, ErrorCode
from app.utils.cos_client import put_object

ONE_K = 1024
ONE_M = 1024 * 1024
MAX_FILE_SIZE = 5 * ONE_M
MAX_IMAGE_SIZE = 10 * ONE_M

ALLOWED_FILE_SUFFIXES = {"jpeg", "jpg", "png", "webp"}
ALLOWED_IMAGE_SUFFIXES = {"jpeg", "jpg", "png", "gif", "webp"}
CONTENT_TYPE_TO_SUFFIX = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/gif": ".gif",
    "image/bmp": ".bmp",
    "image/x-icon": ".ico",
    "image/tiff": ".tif",
    "image/webp": ".webp",
}


def valid_file(file_size: int, filename: str | None) -> None:
    """校验普通文件：大小不超过 5M，后缀为 jpeg/jpg/png/webp。"""
    if file_size > MAX_FILE_SIZE:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M")
    name = filename or ""
    suffix = (Path(name).suffix or "").lstrip(".").lower()
    if suffix not in ALLOWED_FILE_SUFFIXES:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误")


def valid_image_file(file_size: int, filename: str | None, content_type: str | None) -> None:
    """校验图片文件：大小不超过 10M，类型为常见图片。"""
    if file_size > MAX_IMAGE_SIZE:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 10M")
    name = filename or ""
    suffix = (Path(name).suffix or "").lstrip(".").lower()
    if suffix in ALLOWED_IMAGE_SUFFIXES:
        return
    ct = (content_type or "").lower().split(";")[0].strip()
    if ct in CONTENT_TYPE_TO_SUFFIX:
        return
    raise BusinessException(ErrorCode.PARAMS_ERROR, "图片类型错误")


def _build_cos_key(user_id: int, path_part: str, filename: str) -> str:
    """构建 COS 对象键，不含前导斜杠。"""
    safe_name = filename.lstrip("/").split("/")[-1]
    if path_part:
        return f"aitest/{user_id}/{path_part}/{safe_name}"
    return f"aitest/{user_id}/{safe_name}"


def _check_cos_upload_ready() -> None:
    """上传前检查：域名由 bucket+region 默认推导（与 Java 一致），只需配置密钥即可使用。"""
    settings = get_settings()
    if not settings.effective_cos_host:
        raise BusinessException(
            ErrorCode.SYSTEM_ERROR,
            "未配置 COS 域名：请在 .env 中设置 TENCENT_COS_BUCKET、TENCENT_COS_REGION 或 TENCENT_COS_HOST。"
        )
    if not settings.TENCENT_COS_ACCESS_KEY or not settings.TENCENT_COS_SECRET_KEY:
        raise BusinessException(
            ErrorCode.SYSTEM_ERROR,
            "未配置 COS 密钥：请在 .env 中设置 TENCENT_COS_ACCESS_KEY 和 TENCENT_COS_SECRET_KEY（与 Java 的 tencent.cos.accessKey/secretKey 一致）。"
        )


def upload_file(file_path: Path, filename: str, user_id: int) -> str:
    """上传文件到 COS，返回可访问的完整 URL。"""
    _check_cos_upload_ready()
    settings = get_settings()
    key = _build_cos_key(user_id, "", filename)
    put_object(key, file_path)
    return f"{settings.effective_cos_host}/{key}"


def upload_image(file_path: Path, filename: str, user_id: int) -> str:
    """上传图片到 COS，路径为 aitest/{userId}/images/{filename}。"""
    _check_cos_upload_ready()
    settings = get_settings()
    key = _build_cos_key(user_id, "images", filename)
    put_object(key, file_path)
    return f"{settings.effective_cos_host}/{key}"


def upload_file_by_base64(file_base64: str, user_id: int) -> str:
    """Base64 解码后上传，保存为 webp。"""
    _check_cos_upload_ready()
    settings = get_settings()
    try:
        raw = base64.b64decode(file_base64)
    except Exception as e:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "Base64 解码失败") from e
    if len(raw) > 5 * ONE_M:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M")
    uid = secrets.token_hex(8)
    filename = f"{uid}.webp"
    key = f"project_name/{user_id}/{filename}"
    put_object(key, raw)
    return f"{settings.effective_cos_host}/{key}"


def _valid_url_image_response(response: httpx.Response) -> None:
    if response.status_code != 200:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "文件下载失败")
    content_type = (response.headers.get("content-type") or "").split(";")[0].strip().lower()
    if content_type not in CONTENT_TYPE_TO_SUFFIX:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误")
    clen = response.headers.get("content-length")
    if clen and int(clen) > 5 * ONE_M:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 5M")


def upload_file_from_img_url(url: str, user_id: int, is_compress: bool = False) -> str:
    """从图片 URL 下载后上传到 COS。"""
    _check_cos_upload_ready()
    settings = get_settings()
    try:
        with httpx.Client(timeout=10.0) as client:
            response = client.get(url)
    except Exception as e:
        raise BusinessException(ErrorCode.SYSTEM_ERROR, "图片下载失败") from e
    _valid_url_image_response(response)
    content_type = (response.headers.get("content-type") or "").split(";")[0].strip().lower()
    ext = CONTENT_TYPE_TO_SUFFIX.get(content_type, ".jpg")
    uid = secrets.token_hex(8)
    filename = f"{uid}{ext}"
    key = _build_cos_key(user_id, "", filename)
    put_object(key, response.content)
    return f"{settings.effective_cos_host}/{key}"


def sanitize_filename_for_upload(original_filename: str) -> str:
    """若文件名含中文等，用随机字符串替代，保留后缀。"""
    if not original_filename:
        ext = ".png"
    else:
        ext = Path(original_filename).suffix or ".png"
    if re.search(r"[\u4e00-\u9fff]", original_filename or ""):
        return secrets.token_hex(5) + ext
    return original_filename
