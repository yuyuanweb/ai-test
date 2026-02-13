"""
上传接口（与 Java UploadController 对齐，供前端多模态图片上传）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import secrets
import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, Request, UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.file import UploadImageVO
from app.services.file_service import valid_image_file, upload_image
from app.services.user_service import UserService

router = APIRouter(tags=["上传接口"])


@router.post("/image", response_model=BaseResponse[UploadImageVO], summary="图片上传（多模态输入）")
async def upload_image_api(
    request: Request,
    file: UploadFile = File(..., alias="file"),
    db: AsyncSession = Depends(get_db),
):
    login_user = await UserService.get_login_user(db, request)
    content = await file.read()
    file_size = len(content)
    valid_image_file(file_size, file.filename, file.content_type)

    suffix = (Path(file.filename or "").suffix or "").lstrip(".").lower()
    if not suffix and file.content_type:
        ct = (file.content_type or "").lower()
        if "jpeg" in ct or "jpg" in ct:
            suffix = "jpg"
        elif "png" in ct:
            suffix = "png"
        elif "gif" in ct:
            suffix = "gif"
        elif "webp" in ct:
            suffix = "webp"
    if not suffix:
        suffix = "jpg"
    filename = f"{secrets.token_hex(16)}.{suffix}"

    with tempfile.NamedTemporaryFile(delete=False, suffix="." + suffix) as tmp:
        tmp.write(content)
        tmp_path = Path(tmp.name)
    try:
        url = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: upload_image(tmp_path, filename, login_user.id),
        )
        vo = UploadImageVO(
            url=url,
            original_filename=file.filename,
            size=file_size,
            content_type=file.content_type,
        )
        return BaseResponse(code=0, data=vo, message="ok")
    finally:
        tmp_path.unlink(missing_ok=True)
