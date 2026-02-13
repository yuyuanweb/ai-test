"""
文件上传接口（腾讯云 COS）
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
from app.schemas.file import Base64UploadFileRequest, UploadUrlFileRequest
from app.services.file_service import (
    valid_file,
    upload_file as svc_upload_file,
    upload_file_by_base64,
    upload_file_from_img_url,
    sanitize_filename_for_upload,
)
from app.services.user_service import UserService

router = APIRouter(prefix="/file", tags=["文件接口"])


@router.post("/upload/form_data", response_model=BaseResponse[str], summary="文件上传(form_data)")
async def upload_file_by_form_data(
    request: Request,
    file: UploadFile = File(..., alias="file"),
    db: AsyncSession = Depends(get_db),
):
    login_user = await UserService.get_login_user(db, request)
    content = await file.read()
    valid_file(len(content), file.filename)
    with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename or "").suffix) as tmp:
        tmp.write(content)
        tmp_path = Path(tmp.name)
    try:
        url = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: svc_upload_file(tmp_path, file.filename or "file.png", login_user.id),
        )
        return BaseResponse(code=0, data=url, message="ok")
    finally:
        tmp_path.unlink(missing_ok=True)


@router.post("/upload", response_model=BaseResponse[str], summary="文件上传(url params)")
async def upload_file_with_params(
    request: Request,
    file: UploadFile = File(..., alias="file"),
    db: AsyncSession = Depends(get_db),
):
    login_user = await UserService.get_login_user(db, request)
    content = await file.read()
    valid_file(len(content), file.filename)
    filename = sanitize_filename_for_upload(file.filename or "")
    filename = f"{secrets.token_hex(4)}_{filename}"
    with tempfile.NamedTemporaryFile(delete=False, suffix=Path(filename).suffix) as tmp:
        tmp.write(content)
        tmp_path = Path(tmp.name)
    try:
        url = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: svc_upload_file(tmp_path, filename, login_user.id),
        )
        return BaseResponse(code=0, data=url, message="ok")
    finally:
        tmp_path.unlink(missing_ok=True)


@router.post("/upload/base64", response_model=BaseResponse[str], summary="Base64 文件上传")
async def upload_file_base64(
    body: Base64UploadFileRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    login_user = await UserService.get_login_user(db, request)
    url = upload_file_by_base64(body.file_base64, login_user.id)
    return BaseResponse(code=0, data=url, message="ok")


@router.post("/url/upload", response_model=BaseResponse[str], summary="图片链接上传")
async def upload_file_from_url(
    body: UploadUrlFileRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    login_user = await UserService.get_login_user(db, request)
    url = upload_file_from_img_url(body.url, login_user.id, body.compress)
    return BaseResponse(code=0, data=url, message="ok")
