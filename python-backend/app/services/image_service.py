"""
图片生成服务（通过 OpenRouter 多模态，与 Java ImageServiceImpl 一致）
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import base64
import json
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional, Tuple, AsyncGenerator

import httpx
from loguru import logger
from sqlalchemy import select, and_, func, update

from app.core.config import get_settings
from app.core.errors import BusinessException, ErrorCode
from app.db.session import AsyncSessionLocal
from app.models.conversation import Conversation
from app.models.conversation_message import ConversationMessage
from app.models.model import Model
from app.schemas.image import GenerateImageRequest, GeneratedImageVO, ImageStreamChunkVO
from app.services.file_service import _check_cos_upload_ready
from app.services.model_service import ModelService
from app.utils.cos_client import put_object

settings = get_settings()
ONE_M = 1024 * 1024
COS_IMAGE_PREFIX = "aitest/{}/generated/"


def _build_completions_url() -> str:
    base = (settings.OPENROUTER_BASE_URL or "").strip().rstrip("/")
    if not base:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "请配置 OpenRouter 的 OPENROUTER_BASE_URL")
    if base.endswith("/v1"):
        return f"{base}/chat/completions"
    return f"{base}/v1/chat/completions"


def _build_request_body(request: GenerateImageRequest, stream: bool) -> dict:
    messages = []
    if request.reference_image_urls:
        content = [{"type": "text", "text": request.prompt}]
        for url in request.reference_image_urls:
            if url and url.strip():
                content.append({"type": "image_url", "image_url": {"url": url}})
        messages.append({"role": "user", "content": content})
    else:
        messages.append({"role": "user", "content": request.prompt})
    return {
        "model": request.model,
        "modalities": ["text", "image"],
        "stream": stream,
        "messages": messages,
        "n": request.count,
    }


def _mime_to_ext(mime: Optional[str]) -> str:
    if not mime:
        return "png"
    m = (mime or "").split(";")[0].strip().lower()
    if m in ("image/jpeg", "image/jpg"):
        return "jpg"
    if m == "image/png":
        return "png"
    if m == "image/webp":
        return "webp"
    if m == "image/gif":
        return "gif"
    return "png"


def _handle_image_data(data: str, user_id: int) -> str:
    """若为 data:image/... base64 则解码上传 COS 并返回 URL；否则原样返回。"""
    if not data.startswith("data:image"):
        return data
    _check_cos_upload_ready()
    try:
        comma = data.find(",")
        if comma <= 0:
            raise BusinessException(ErrorCode.SYSTEM_ERROR, "图片数据格式错误")
        header = data[5:comma]
        parts = header.split(";")
        mime = parts[0].strip() if parts else "image/png"
        b64 = data[comma + 1:]
        raw = base64.b64decode(b64)
        if len(raw) > 10 * ONE_M:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "生成图片过大，请减少分辨率或数量")
        ext = _mime_to_ext(mime)
        filename = f"{uuid.uuid4().hex[:16]}.{ext}"
        key = (COS_IMAGE_PREFIX.format(user_id) + filename).replace("//", "/")
        put_object(key, raw)
        return f"{settings.effective_cos_host}/{key}"
    except BusinessException:
        raise
    except Exception as e:
        logger.error("处理生成图片数据失败: {}", e)
        raise BusinessException(ErrorCode.SYSTEM_ERROR, "处理生成图片数据失败：" + str(e))


async def _get_next_message_index(db, conversation_id: str) -> int:
    r = await db.execute(
        select(func.coalesce(func.max(ConversationMessage.message_index), -1)).where(
            and_(
                ConversationMessage.conversation_id == conversation_id,
                ConversationMessage.is_delete == 0,
            )
        )
    )
    n = r.scalar() or -1
    return int(n) + 1


async def _create_conversation_for_image_gen(user_id: int, request: GenerateImageRequest) -> str:
    conversation_id = str(uuid.uuid4())
    models = request.models if request.models else [request.model]
    title = (request.prompt[:50] + "...") if len(request.prompt) > 50 else request.prompt
    conv_type = (request.conversation_type or "side_by_side").strip()
    if conv_type not in ("side_by_side", "prompt_lab", "battle"):
        conv_type = "side_by_side"
    now = datetime.now()
    async with AsyncSessionLocal() as db:
        conv = Conversation(
            id=conversation_id,
            user_id=user_id,
            title=title,
            conversation_type=conv_type,
            code_preview_enabled=0,
            is_anonymous=0,
            models=models,
            total_tokens=0,
            total_cost=Decimal("0"),
            create_time=now,
            update_time=now,
            is_delete=0,
        )
        db.add(conv)
        await db.commit()
    logger.info("为图片生成创建新会话: conversationId={}, conversationType={}", conversation_id, conv_type)
    return conversation_id


async def _save_to_conversation(
    conversation_id: str,
    user_id: int,
    request: GenerateImageRequest,
    result_list: List[GeneratedImageVO],
    input_tokens: int,
    output_tokens: int,
    total_tokens: int,
    cost: Optional[float],
    reasoning: Optional[str],
) -> Optional[int]:
    try:
        async with AsyncSessionLocal() as db:
            user_message_index = (
                request.message_index
                if request.message_index is not None
                else await _get_next_message_index(db, conversation_id)
            )
            variant_index = request.variant_index
            assistant_message_index = user_message_index

            user_msg = ConversationMessage(
                id=str(uuid.uuid4()),
                conversation_id=conversation_id,
                user_id=user_id,
                message_index=user_message_index,
                variant_index=variant_index,
                role="user",
                content=request.prompt,
                images=json.dumps(request.reference_image_urls) if request.reference_image_urls else None,
                is_delete=0,
            )
            db.add(user_msg)

            urls = [vo.url for vo in result_list if vo.url]
            cost_val = Decimal(str(cost)) if cost is not None else Decimal("0")
            assistant_msg = ConversationMessage(
                id=str(uuid.uuid4()),
                conversation_id=conversation_id,
                user_id=user_id,
                message_index=assistant_message_index,
                variant_index=variant_index,
                role="assistant",
                model_name=request.model,
                content=f"已生成 {len(urls)} 张图片",
                images=urls,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                cost=cost_val,
                reasoning=reasoning,
                is_delete=0,
            )
            db.add(assistant_msg)

            await db.execute(
                update(Conversation)
                .where(Conversation.id == conversation_id, Conversation.is_delete == 0)
                .values(
                    total_tokens=Conversation.total_tokens + total_tokens,
                    total_cost=Conversation.total_cost + cost_val,
                )
            )
            await db.commit()
        logger.info("图片生成结果已保存到会话: conversationId={}, messageIndex={}, 图片数={}", conversation_id, user_message_index, len(urls))
        return user_message_index
    except Exception as e:
        logger.error("保存图片生成结果到会话失败: conversationId={}", conversation_id, exc_info=True)
        return None


async def _save_failed_message(
    request: GenerateImageRequest, user_id: int, error_message: str
) -> Tuple[Optional[str], Optional[int]]:
    try:
        conversation_id = request.conversation_id or (await _create_conversation_for_image_gen(user_id, request))
        async with AsyncSessionLocal() as db:
            message_index = (
                request.message_index
                if request.message_index is not None
                else await _get_next_message_index(db, conversation_id)
            )
            user_msg = ConversationMessage(
                id=str(uuid.uuid4()),
                conversation_id=conversation_id,
                user_id=user_id,
                message_index=message_index,
                variant_index=request.variant_index,
                role="user",
                content=request.prompt,
                images=json.dumps(request.reference_image_urls) if request.reference_image_urls else None,
                is_delete=0,
            )
            db.add(user_msg)
            assistant_msg = ConversationMessage(
                id=str(uuid.uuid4()),
                conversation_id=conversation_id,
                user_id=user_id,
                message_index=message_index,
                variant_index=request.variant_index,
                role="assistant",
                content="",
                model_name=request.model or "",
                is_delete=0,
            )
            db.add(assistant_msg)
            await db.commit()
        return conversation_id, message_index
    except Exception as e:
        logger.error("保存失败消息时出错: {}", e)
        return None, None


async def generate_images(request: GenerateImageRequest, user_id: int) -> tuple[List[GeneratedImageVO], Optional[str]]:
    """
    调用 OpenRouter 生成图片，上传 COS，可选保存到会话。
    返回 (GeneratedImageVO 列表, reasoning 文本)。
    """
    if not request.model or not request.model.strip():
        raise BusinessException(ErrorCode.PARAMS_ERROR, "模型名称不能为空")
    if request.count is None or request.count < 1:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "生成数量必须大于 0")
    if user_id is None or user_id <= 0:
        raise BusinessException(ErrorCode.NOT_LOGIN_ERROR)

    from app.db.session import AsyncSessionLocal
    async with AsyncSessionLocal() as db:
        model_service = ModelService(db)
        model = await model_service.get_model_by_id(request.model)
    if model is None:
        raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在或已下线")
    if not getattr(model, "supports_multimodal", 0):
        raise BusinessException(ErrorCode.PARAMS_ERROR, "当前模型不支持图片多模态，请更换模型")

    url = _build_completions_url()
    body = _build_request_body(request, stream=False)
    headers = {
        "Authorization": f"Bearer {settings.OPENROUTER_API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://codefather.cn",
        "X-Title": "AI Evaluation Platform",
    }
    async with httpx.AsyncClient(timeout=120.0) as client:
        resp = await client.post(url, json=body, headers=headers)
    if resp.status_code != 200:
        raise BusinessException(ErrorCode.SYSTEM_ERROR, f"调用图片生成服务失败: {resp.text[:200]}")
    resp_text = resp.text or ""
    if resp_text.lstrip().lower().startswith("<!doctype") or resp_text.lstrip().startswith("<html"):
        raise BusinessException(
            ErrorCode.SYSTEM_ERROR,
            "图片服务返回了 HTML 而非 API 数据，请检查 .env 中 OPENROUTER_BASE_URL 是否指向 OpenRouter（应为 https://openrouter.ai/api），不要填前端地址（如 localhost:5173）。"
        )
    try:
        data = resp.json()
    except Exception as e:
        raise BusinessException(
            ErrorCode.SYSTEM_ERROR,
            f"图片服务返回无法解析为 JSON，请确认 OPENROUTER_BASE_URL=https://openrouter.ai/api。响应预览: {(resp_text[:120] + '...') if len(resp_text) > 120 else resp_text}"
        ) from e
    choices = data.get("choices") or []
    usage = data.get("usage") or {}
    prompt_tokens = usage.get("prompt_tokens") or 0
    completion_tokens = usage.get("completion_tokens") or 0
    total_tokens = usage.get("total_tokens") or (prompt_tokens + completion_tokens)
    cost = usage.get("cost")
    reasoning_parts = []
    result_list: List[GeneratedImageVO] = []
    requested_count = request.count or 1
    image_index = 0

    for choice in choices:
        if len(result_list) >= requested_count:
            break
        msg = choice.get("message") or {}
        if msg.get("content"):
            reasoning_parts.append(msg["content"])
        images = msg.get("images") or []
        for img_part in images:
            if len(result_list) >= requested_count:
                break
            if not img_part or not isinstance(img_part, dict):
                continue
            img_url_obj = img_part.get("image_url") or img_part.get("imageUrl")
            if not img_url_obj:
                continue
            url_data = img_url_obj.get("url") or ""
            if not url_data:
                continue
            image_url = _handle_image_data(url_data, user_id)
            result_list.append(
                GeneratedImageVO(
                    url=image_url,
                    model_name=request.model,
                    index=image_index,
                    input_tokens=prompt_tokens,
                    output_tokens=completion_tokens,
                    total_tokens=total_tokens,
                    cost=cost,
                )
            )
            image_index += 1

    reasoning = "\n".join(reasoning_parts) if reasoning_parts else None
    if not result_list:
        raise BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：未解析到图片结果")

    if total_tokens > 0 and cost is not None:
        cost_decimal = Decimal(str(cost))
        async with AsyncSessionLocal() as db:
            await db.execute(
                update(Model)
                .where(Model.id == request.model, Model.is_delete == 0)
                .values(
                    total_tokens=Model.total_tokens + total_tokens,
                    total_cost=Model.total_cost + cost_decimal,
                )
            )
            await db.commit()

    conversation_id = request.conversation_id
    saved_message_index = None
    if conversation_id and conversation_id.strip():
        async with AsyncSessionLocal() as db:
            r = await db.execute(select(Conversation).where(Conversation.id == conversation_id, Conversation.is_delete == 0))
            conv = r.scalar_one_or_none()
        if conv and conv.user_id == user_id:
            saved_message_index = await _save_to_conversation(
                conversation_id, user_id, request, result_list,
                prompt_tokens, completion_tokens, total_tokens, cost, reasoning
            )
            for vo in result_list:
                vo.conversation_id = conversation_id
                vo.message_index = saved_message_index
    elif request.models:
        conversation_id = await _create_conversation_for_image_gen(user_id, request)
        saved_message_index = await _save_to_conversation(
            conversation_id, user_id, request, result_list,
            prompt_tokens, completion_tokens, total_tokens, cost, reasoning
        )
        for vo in result_list:
            vo.conversation_id = conversation_id
            vo.message_index = saved_message_index

    return result_list, reasoning


async def generate_images_stream(
    request: GenerateImageRequest, user_id: int
) -> AsyncGenerator[str, None]:
    """
    SSE 流式返回：先调用 generate_images，再按 thinking -> image -> done 顺序推送；异常时推送 error。
    保证最终一定推送 done 或 error，避免前端一直等待。
    """
    done_sent = False

    def send(chunk: ImageStreamChunkVO) -> str:
        nonlocal done_sent
        if chunk.type in ("done", "error"):
            done_sent = True
        return f"data: {chunk.model_dump_json(by_alias=True, exclude_none=True)}\n\n"

    try:
        images, reasoning = await generate_images(request, user_id)
        if reasoning and reasoning.strip():
            yield send(
                ImageStreamChunkVO(
                    type="thinking",
                    thinking=reasoning,
                    full_thinking=reasoning,
                    model_name=request.model,
                    variant_index=request.variant_index,
                )
            )
        conversation_id = None
        message_index = None
        for img in images:
            if img.conversation_id:
                conversation_id = img.conversation_id
            if img.message_index is not None:
                message_index = img.message_index
            yield send(
                ImageStreamChunkVO(
                    type="image",
                    image=img,
                    conversation_id=conversation_id or img.conversation_id,
                    message_index=message_index if message_index is not None else img.message_index,
                    variant_index=request.variant_index,
                    model_name=request.model,
                )
            )
        yield send(
            ImageStreamChunkVO(
                type="done",
                conversation_id=conversation_id,
                message_index=message_index,
                variant_index=request.variant_index,
                model_name=request.model,
                full_thinking=reasoning,
                images=images,
            )
        )
    except Exception as e:
        err_conv_id, err_msg_index = None, None
        try:
            err_conv_id, err_msg_index = await _save_failed_message(request, user_id, str(e))
        except Exception:
            pass
        yield send(
            ImageStreamChunkVO(
                type="error",
                error=str(e),
                conversation_id=err_conv_id,
                message_index=err_msg_index,
                variant_index=request.variant_index,
                model_name=request.model,
            )
        )
    finally:
        if not done_sent:
            yield f"data: {{\"type\":\"done\",\"modelName\":\"{request.model or ''}\"}}\n\n"
            logger.warning("图片流式响应未正常发送 done，已在 finally 中补发")
