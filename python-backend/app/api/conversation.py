"""
对话接口
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional
from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas.common import BaseResponse
from app.schemas.conversation import (
    CreateConversationRequest,
    ChatRequest,
    SideBySideRequest,
    PromptLabRequest,
    CodeModeRequest,
    CodeModePromptLabRequest,
    BattleRequest,
    DeleteConversationRequest,
    ConversationVO,
    ConversationQueryRequest,
    ConversationMessageVO,
    BattleModelMappingVO
)
from app.services.user_service import UserService
from app.services.conversation_service import ConversationService
from app.utils.rate_limit import check_rate_limit, RateLimitType

router = APIRouter(prefix="/conversation", tags=["对话接口"])


def _get_redis(request: Request):
    if request is None:
        return None
    return getattr(request.app.state, "redis_client", None)


@router.post("/create", response_model=BaseResponse[str], summary="创建对话")
async def create_conversation(
    request_data: CreateConversationRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    创建对话
    """
    login_user = await UserService.get_login_user(db, http_request)
    
    conversation_service = ConversationService(db, _get_redis(http_request))
    conversation_id = await conversation_service.create_conversation(
        request_data,
        login_user.id
    )
    
    return BaseResponse(code=0, data=conversation_id, message="ok")


@router.post("/chat/stream", summary="基础对话(流式)")
async def chat_stream(
    request_data: ChatRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    基础对话（流式响应）
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)
    
    conversation_service = ConversationService(db, _get_redis(http_request))
    
    return StreamingResponse(
        conversation_service.chat_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/battle/stream", summary="Battle匿名模型对比(流式)")
async def battle_stream(
    request_data: BattleRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Battle 匿名模型对比（流式响应）
    模型匿名显示为模型A、模型B，适用于公平对比避免品牌偏见
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)

    conversation_service = ConversationService(db, _get_redis(http_request))

    return StreamingResponse(
        conversation_service.battle_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/side-by-side/stream", summary="Side-by-Side多模型并排对比(流式)")
async def side_by_side_stream(
    request_data: SideBySideRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Side-by-Side 多模型并排对比（流式响应）
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)
    
    conversation_service = ConversationService(db, _get_redis(http_request))
    
    return StreamingResponse(
        conversation_service.side_by_side_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/prompt-lab/stream", summary="Prompt Lab单模型多提示词对比(流式)")
async def prompt_lab_stream(
    request_data: PromptLabRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Prompt Lab 单模型多提示词对比（流式响应）
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)

    conversation_service = ConversationService(db, _get_redis(http_request))

    return StreamingResponse(
        conversation_service.prompt_lab_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/code-mode/stream", summary="代码模式(流式)")
async def code_mode_stream(
    request_data: CodeModeRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Code Mode 代码模式（流式响应）
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)

    conversation_service = ConversationService(db, _get_redis(http_request))

    return StreamingResponse(
        conversation_service.code_mode_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.post("/code-mode/prompt-lab/stream", summary="代码模式提示词实验(流式)")
async def code_mode_prompt_lab_stream(
    request_data: CodeModePromptLabRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Code Mode 提示词实验（流式响应）
    """
    await check_rate_limit(
        _get_redis(http_request), http_request,
        RateLimitType.USER, 5, 60,
        message="AI 对话请求过于频繁，请稍后再试"
    )
    login_user = await UserService.get_login_user(db, http_request)

    conversation_service = ConversationService(db, _get_redis(http_request))

    return StreamingResponse(
        conversation_service.code_mode_prompt_lab_stream(request_data, login_user.id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


@router.get("/get", response_model=BaseResponse[ConversationVO], summary="获取对话详情")
async def get_conversation(
    conversationId: str,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取对话详情
    """
    login_user = await UserService.get_login_user(db, http_request)
    
    conversation_service = ConversationService(db, _get_redis(http_request))
    conversation = await conversation_service.get_conversation(
        conversationId,
        login_user.id
    )
    
    return BaseResponse(code=0, data=conversation, message="ok")


@router.post("/delete", response_model=BaseResponse[bool], summary="删除对话")
async def delete_conversation(
    request_data: DeleteConversationRequest,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    删除对话
    """
    login_user = await UserService.get_login_user(db, http_request)
    
    conversation_service = ConversationService(db, _get_redis(http_request))
    result = await conversation_service.delete_conversation(
        request_data.id,
        login_user.id
    )
    
    return BaseResponse(code=0, data=result, message="ok")


@router.get("/list", response_model=BaseResponse[dict], summary="分页查询对话列表")
async def list_conversations(
    pageNum: int = 1,
    pageSize: int = 50,
    conversation_type: str = None,
    codePreviewEnabled: int = None,
    http_request: Request = None,
    db: AsyncSession = Depends(get_db)
):
    """
    分页查询对话列表
    """
    login_user = await UserService.get_login_user(db, http_request)

    code_preview_enabled = None
    if codePreviewEnabled is not None:
        code_preview_enabled = codePreviewEnabled == 1

    conversation_service = ConversationService(db, _get_redis(http_request))
    conversations, total = await conversation_service.list_conversations(
        user_id=login_user.id,
        page_num=pageNum,
        page_size=pageSize,
        conversation_type=conversation_type,
        code_preview_enabled=code_preview_enabled
    )
    
    return BaseResponse(
        code=0,
        data={
            "records": conversations,
            "total": total,
            "pageNum": pageNum,
            "pageSize": pageSize
        },
        message="ok"
    )


@router.get("/messages", response_model=BaseResponse[list], summary="获取对话消息列表")
async def get_conversation_messages(
    http_request: Request,
    db: AsyncSession = Depends(get_db),
    conversationId: Optional[str] = None,
    conversation_id: Optional[str] = None,
):
    """
    获取对话消息列表。查询参数支持 conversationId 或 conversation_id。
    """
    cid = conversationId or conversation_id
    if not cid or not cid.strip():
        from app.core.errors import BusinessException, ErrorCode
        raise BusinessException(ErrorCode.PARAMS_ERROR, "conversationId 不能为空")
    login_user = await UserService.get_login_user(db, http_request)
    conversation_service = ConversationService(db, _get_redis(http_request))
    messages = await conversation_service.get_conversation_messages(cid.strip(), login_user.id)
    return BaseResponse(code=0, data=messages if messages is not None else [], message="ok")


@router.get("/battle/mapping", response_model=BaseResponse[BattleModelMappingVO], summary="获取Battle模式模型映射关系（揭晓答案）")
async def get_battle_model_mapping(
    conversationId: str,
    http_request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    获取 Battle 模式的模型映射关系（揭晓答案）
    返回匿名标识到真实模型名称的映射
    """
    login_user = await UserService.get_login_user(db, http_request)

    conversation_service = ConversationService(db, _get_redis(http_request))
    mapping = await conversation_service.get_battle_model_mapping(
        conversationId,
        login_user.id
    )

    return BaseResponse(code=0, data=BattleModelMappingVO(mapping=mapping), message="ok")
