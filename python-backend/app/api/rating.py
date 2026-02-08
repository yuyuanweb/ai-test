"""
评分接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import List
from fastapi import APIRouter, Depends, Request, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.rating import RatingAddRequest, RatingVO
from app.services.user_service import UserService
from app.services.rating_service import RatingService
from app.core.errors import BusinessException, ErrorCode

router = APIRouter(prefix="/rating", tags=["评分接口"])


@router.post("/add", response_model=BaseResponse[bool], summary="添加或更新评分")
async def add_rating(
    request_data: RatingAddRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    添加或更新评分
    """
    if not request_data.conversation_id:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "对话ID不能为空")
    
    if request_data.message_index is None:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "消息序号不能为空")
    
    if not request_data.rating_type:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "评分类型不能为空")
    
    login_user = await UserService.get_login_user(db, http_request)
    
    rating_service = RatingService(db)
    result = await rating_service.save_or_update_rating(
        conversation_id=request_data.conversation_id,
        message_index=request_data.message_index,
        user_id=login_user.id,
        rating_type=request_data.rating_type,
        winner_model=request_data.winner_model,
        loser_model=request_data.loser_model,
        winner_variant_index=request_data.winner_variant_index,
        loser_variant_index=request_data.loser_variant_index
    )
    
    return BaseResponse(code=0, data=result, message="ok")


@router.get("/get", response_model=BaseResponse[RatingVO], summary="获取评分")
async def get_rating(
    conversationId: str = Query(..., description="对话ID"),
    messageIndex: int = Query(..., description="消息序号"),
    http_request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    获取评分
    """
    if not conversationId:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "对话ID不能为空")
    
    if messageIndex is None:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "消息序号不能为空")
    
    login_user = await UserService.get_login_user(db, http_request)
    
    rating_service = RatingService(db)
    rating = await rating_service.get_rating(
        conversationId,
        messageIndex,
        login_user.id
    )
    
    return BaseResponse(code=0, data=rating, message="ok")


@router.get("/list", response_model=BaseResponse[List[RatingVO]], summary="获取整个会话的所有评分")
async def get_ratings_by_conversation(
    conversationId: str = Query(..., description="对话ID"),
    http_request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    获取整个会话的所有评分
    """
    if not conversationId:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "对话ID不能为空")
    
    login_user = await UserService.get_login_user(db, http_request)
    
    rating_service = RatingService(db)
    ratings = await rating_service.get_ratings_by_conversation(
        conversationId,
        login_user.id
    )
    
    return BaseResponse(code=0, data=ratings, message="ok")


@router.delete("/delete", response_model=BaseResponse[bool], summary="删除评分")
async def delete_rating(
    conversationId: str = Query(..., description="对话ID"),
    messageIndex: int = Query(..., description="消息序号"),
    http_request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    删除评分
    """
    if not conversationId:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "对话ID不能为空")
    
    if messageIndex is None:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "消息序号不能为空")
    
    login_user = await UserService.get_login_user(db, http_request)
    
    rating_service = RatingService(db)
    result = await rating_service.delete_rating(
        conversationId,
        messageIndex,
        login_user.id
    )
    
    return BaseResponse(code=0, data=result, message="ok")
