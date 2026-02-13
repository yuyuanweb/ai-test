"""
图片生成接口（与 Java ImageController 一致）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from fastapi import APIRouter, Request, Depends
from fastapi.responses import StreamingResponse

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.image import GenerateImageRequest, GeneratedImageVO, ImageStreamChunkVO
from app.services.user_service import UserService
from app.services.image_service import generate_images, generate_images_stream
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter(prefix="/image", tags=["图片生成"])


@router.post("/generate", response_model=BaseResponse[list], summary="生成图片（文本/图生图）")
async def generate_image(
    request: GenerateImageRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """生成图片，返回图片 URL 列表。"""
    login_user = await UserService.get_login_user(db, http_request)
    result_list, _ = await generate_images(request, login_user.id)
    return BaseResponse(code=0, data=[r.model_dump(by_alias=True, exclude_none=True) for r in result_list], message="ok")


@router.post("/generate/stream", summary="流式生成图片（输出思考过程）")
async def generate_image_stream(
    request: GenerateImageRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
):
    """SSE 流式返回：thinking -> image -> done / error。"""
    login_user = await UserService.get_login_user(db, http_request)
    return StreamingResponse(
        generate_images_stream(request, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
