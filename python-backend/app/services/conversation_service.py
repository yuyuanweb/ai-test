"""
对话服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
import json
import uuid
import time
from typing import List, Optional, Dict, Any, AsyncGenerator
from decimal import Decimal
from datetime import datetime
from sqlalchemy import select, and_, func, or_, update
from sqlalchemy.ext.asyncio import AsyncSession

from openai import AsyncOpenAI
from loguru import logger

from app.db.session import AsyncSessionLocal

from app.models.conversation import Conversation
from app.models.conversation_message import ConversationMessage
from app.models.model import Model
from app.schemas.conversation import (
    CreateConversationRequest,
    SideBySideRequest,
    PromptLabRequest,
    CodeModeRequest,
    CodeModePromptLabRequest,
    StreamChunkVO,
    ConversationVO,
    ConversationMessageVO
)
from app.utils.code_extractor import extract_code_blocks
from app.utils.prompt_guardrail import validate as validate_prompt
from app.core.errors import BusinessException, ErrorCode
from app.core.config import get_settings
from app.utils.cost_calculator import CostCalculator
from app.utils.model_pricing_cache import get_model_pricing_cached_async

settings = get_settings()

MIN_PROMPT_VARIANTS_COUNT = 2
MAX_PROMPT_VARIANTS_COUNT = 5
MIN_MODELS_COUNT = 1
MAX_MODELS_COUNT = 8

CODE_MODE_SYSTEM_PROMPT = """
你是一个专业的前端开发专家。用户会向你描述想要创建的网站或应用，你需要生成完整的HTML代码。

代码要求：
1. 生成完整的HTML网页代码（包含HTML、CSS和JavaScript）
2. 将HTML、CSS和JavaScript都写在同一个HTML文件中
3. CSS写在<style>标签内，JavaScript写在<script>标签内
4. 代码要完整可运行，可以直接在浏览器中打开
5. 使用现代化的CSS样式，界面要美观、专业
6. 确保代码有良好的注释

回复格式：
- 你可以先简要说明设计思路或实现要点
- 然后使用Markdown代码块输出完整的HTML代码
- 代码块格式：
```html
<!DOCTYPE html>
<html>
...
</html>
```
- 代码后可以补充使用说明或功能说明

注意：虽然可以添加文字说明，但核心重点是生成可运行的HTML代码。
"""


class ConversationService:
    """
    对话服务类
    """
    
    def __init__(self, db: AsyncSession, redis_client=None):
        self.db = db
        self.redis_client = redis_client
        self.openai_client = AsyncOpenAI(
            api_key=settings.OPENROUTER_API_KEY,
            base_url=settings.OPENROUTER_BASE_URL
        )
    
    async def create_conversation(
        self,
        request: CreateConversationRequest,
        user_id: int
    ) -> str:
        """
        创建对话
        
        Args:
            request: 创建对话请求
            user_id: 用户ID
            
        Returns:
            对话ID
        """
        if not request.conversation_type:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "对话类型不能为空")
        
        if not request.models or len(request.models) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空")
        
        conversation_id = str(uuid.uuid4())
        title = request.title if request.title else "新对话"
        
        conversation = Conversation(
            id=conversation_id,
            user_id=user_id,
            title=title,
            conversation_type=request.conversation_type,
            models=json.dumps(request.models),
            code_preview_enabled=1 if request.code_preview_enabled else 0,
            is_anonymous=0,
            model_mapping=None,
            total_tokens=0,
            total_cost=Decimal('0'),
            is_delete=0
        )
        
        self.db.add(conversation)
        await self.db.commit()
        
        return conversation_id
    
    async def chat_stream(
        self,
        request: 'ChatRequest',
        user_id: int
    ) -> AsyncGenerator[str, None]:
        """
        基础对话（SSE 流式响应）
        
        Args:
            request: 对话请求
            user_id: 用户ID
            
        Yields:
            SSE 格式的数据流
        """
        if not request.model or not request.model.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型不能为空")
        
        if not request.message or not request.message.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空")
        validate_prompt(request.message)

        conversation_id = request.conversation_id
        if not conversation_id:
            conversation_id = str(uuid.uuid4())
            conversation = Conversation(
                id=conversation_id,
                user_id=user_id,
                title=self._generate_title(request.message),
                conversation_type="chat",
                models=json.dumps([request.model]),
                code_preview_enabled=0,
                is_anonymous=0,
                total_tokens=0,
                total_cost=Decimal('0'),
                is_delete=0
            )
            self.db.add(conversation)
            await self.db.commit()
        
        user_message_index = await self._save_user_message(
            conversation_id,
            user_id,
            request.message,
            request.image_urls
        )
        
        assistant_message_index = user_message_index + 1
        
        async for event in self._stream_single_model(
            conversation_id,
            user_id,
            request.model,
            request.message,
            assistant_message_index,
            None,
            request.image_urls,
            request.web_search_enabled or False
        ):
            yield event
    
    async def side_by_side_stream(
        self,
        request: SideBySideRequest,
        user_id: int
    ) -> AsyncGenerator[str, None]:
        """
        Side-by-Side 多模型并排对比（SSE 流式响应）
        
        Args:
            request: 并排对比请求
            user_id: 用户ID
            
        Yields:
            SSE 格式的数据流
        """
        if not request.models or len(request.models) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空")
        
        if len(request.models) > 8:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "最多支持8个模型")
        
        if not request.prompt or not request.prompt.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空")
        validate_prompt(request.prompt)

        conversation_id = request.conversation_id
        if not conversation_id:
            conversation_id = str(uuid.uuid4())
            conversation = Conversation(
                id=conversation_id,
                user_id=user_id,
                title=self._generate_title(request.prompt),
                conversation_type="side_by_side",
                models=json.dumps(request.models),
                code_preview_enabled=0,
                is_anonymous=0,
                total_tokens=0,
                total_cost=Decimal('0'),
                is_delete=0
            )
            self.db.add(conversation)
            await self.db.commit()
        
        user_message_index = await self._save_user_message(
            conversation_id,
            user_id,
            request.prompt,
            request.image_urls
        )
        
        assistant_message_index = user_message_index + 1
        
        tasks = []
        for model_name in request.models:
            task = self._stream_single_model(
                conversation_id,
                user_id,
                model_name,
                request.prompt,
                assistant_message_index,
                None,
                request.image_urls,
                request.web_search_enabled
            )
            tasks.append(task)
        
        async for event in self._merge_streams(tasks, request.models):
            yield event

    async def prompt_lab_stream(
        self,
        request: PromptLabRequest,
        user_id: int
    ) -> AsyncGenerator[str, None]:
        """
        Prompt Lab 单模型多提示词对比（SSE 流式响应）
        """
        self._validate_prompt_lab_request(request)

        conversation_id = request.conversation_id
        if not conversation_id:
            conversation_id = str(uuid.uuid4())
            conversation = Conversation(
                id=conversation_id,
                user_id=user_id,
                title=self._generate_title(request.prompt_variants[0]),
                conversation_type="prompt_lab",
                models=json.dumps([request.model]),
                code_preview_enabled=0,
                is_anonymous=0,
                total_tokens=0,
                total_cost=Decimal('0'),
                is_delete=0
            )
            self.db.add(conversation)
            await self.db.commit()

        user_message_index = await self._get_next_message_index(conversation_id)
        assistant_message_index = user_message_index + 1

        for i, prompt_variant in enumerate(request.prompt_variants):
            image_urls = None
            if request.variant_image_urls and i < len(request.variant_image_urls):
                image_urls = request.variant_image_urls[i]

            await self._save_prompt_lab_user_message(
                conversation_id,
                user_id,
                user_message_index,
                i,
                prompt_variant,
                image_urls
            )

        tasks = []
        for i, prompt_variant in enumerate(request.prompt_variants):
            image_urls = None
            if request.variant_image_urls and i < len(request.variant_image_urls):
                image_urls = request.variant_image_urls[i]

            task = self._stream_single_model(
                conversation_id,
                user_id,
                request.model,
                prompt_variant,
                assistant_message_index,
                i,
                image_urls,
                request.web_search_enabled or False
            )
            tasks.append(task)

        model_names = [request.model] * len(request.prompt_variants)
        async for event in self._merge_streams(tasks, model_names):
            yield event

    async def code_mode_stream(
        self,
        request: CodeModeRequest,
        user_id: int
    ) -> AsyncGenerator[str, None]:
        """
        Code Mode 代码模式（SSE 流式响应）
        """
        self._validate_code_mode_request(request)

        conversation_id = request.conversation_id
        if not conversation_id or not conversation_id.strip():
            conversation_id = str(uuid.uuid4())
            conversation = Conversation(
                id=conversation_id,
                user_id=user_id,
                title=self._generate_title(request.prompt),
                conversation_type="side_by_side",
                models=json.dumps(request.models),
                code_preview_enabled=1,
                is_anonymous=0,
                total_tokens=0,
                total_cost=Decimal('0'),
                is_delete=0
            )
            self.db.add(conversation)
            await self.db.commit()

        user_message_index = await self._save_user_message(
            conversation_id,
            user_id,
            request.prompt,
            request.image_urls
        )
        assistant_message_index = user_message_index + 1

        tasks = []
        for model_name in request.models:
            task = self._stream_single_model(
                conversation_id,
                user_id,
                model_name,
                request.prompt,
                assistant_message_index,
                None,
                request.image_urls,
                request.web_search_enabled or False,
                CODE_MODE_SYSTEM_PROMPT
            )
            tasks.append(task)

        async for event in self._merge_streams(tasks, request.models):
            yield event

    async def code_mode_prompt_lab_stream(
        self,
        request: CodeModePromptLabRequest,
        user_id: int
    ) -> AsyncGenerator[str, None]:
        """
        Code Mode 提示词实验（SSE 流式响应）
        """
        self._validate_code_mode_prompt_lab_request(request)

        conversation_id = request.conversation_id
        if not conversation_id:
            conversation_id = str(uuid.uuid4())
            conversation = Conversation(
                id=conversation_id,
                user_id=user_id,
                title=self._generate_title(request.prompt_variants[0]),
                conversation_type="prompt_lab",
                models=json.dumps([request.model]),
                code_preview_enabled=1,
                is_anonymous=0,
                total_tokens=0,
                total_cost=Decimal('0'),
                is_delete=0
            )
            self.db.add(conversation)
            await self.db.commit()

        user_message_index = await self._get_next_message_index(conversation_id)
        assistant_message_index = user_message_index + 1

        for i, prompt_variant in enumerate(request.prompt_variants):
            image_urls = None
            if request.variant_image_urls and i < len(request.variant_image_urls):
                image_urls = request.variant_image_urls[i]

            await self._save_prompt_lab_user_message(
                conversation_id,
                user_id,
                user_message_index,
                i,
                prompt_variant,
                image_urls
            )

        tasks = []
        for i, prompt_variant in enumerate(request.prompt_variants):
            image_urls = None
            if request.variant_image_urls and i < len(request.variant_image_urls):
                image_urls = request.variant_image_urls[i]

            task = self._stream_single_model(
                conversation_id,
                user_id,
                request.model,
                prompt_variant,
                assistant_message_index,
                i,
                image_urls,
                request.web_search_enabled or False,
                CODE_MODE_SYSTEM_PROMPT
            )
            tasks.append(task)

        model_names = [request.model] * len(request.prompt_variants)
        async for event in self._merge_streams(tasks, model_names):
            yield event

    def _validate_code_mode_request(self, request: CodeModeRequest):
        """校验 Code Mode 请求参数"""
        if not request.models or len(request.models) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空")
        if len(request.models) < MIN_MODELS_COUNT or len(request.models) > MAX_MODELS_COUNT:
            raise BusinessException(
                ErrorCode.PARAMS_ERROR,
                f"模型数量必须在{MIN_MODELS_COUNT}-{MAX_MODELS_COUNT}个之间"
            )
        if not request.prompt or not request.prompt.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空")
        validate_prompt(request.prompt)

    def _validate_code_mode_prompt_lab_request(self, request: CodeModePromptLabRequest):
        """校验 Code Mode 提示词实验请求参数"""
        if not request.model or not request.model.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型不能为空")
        if not request.prompt_variants or len(request.prompt_variants) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词变体列表不能为空")
        if len(request.prompt_variants) < MIN_PROMPT_VARIANTS_COUNT or len(request.prompt_variants) > MAX_PROMPT_VARIANTS_COUNT:
            raise BusinessException(
                ErrorCode.PARAMS_ERROR,
                f"提示词变体数量必须在{MIN_PROMPT_VARIANTS_COUNT}-{MAX_PROMPT_VARIANTS_COUNT}个之间"
            )
        for v in request.prompt_variants:
            validate_prompt(v)

    def _validate_prompt_lab_request(self, request: PromptLabRequest):
        """校验 Prompt Lab 请求参数"""
        if not request.model or not request.model.strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "模型不能为空")
        if not request.prompt_variants or len(request.prompt_variants) == 0:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "提示词变体列表不能为空")
        if len(request.prompt_variants) < MIN_PROMPT_VARIANTS_COUNT or len(request.prompt_variants) > MAX_PROMPT_VARIANTS_COUNT:
            raise BusinessException(
                ErrorCode.PARAMS_ERROR,
                f"提示词变体数量必须在{MIN_PROMPT_VARIANTS_COUNT}-{MAX_PROMPT_VARIANTS_COUNT}个之间"
            )
        for v in request.prompt_variants:
            validate_prompt(v)

    async def _get_next_message_index(self, conversation_id: str) -> int:
        """获取下一个可用的消息索引"""
        result = await self.db.execute(
            select(func.max(ConversationMessage.message_index))
            .where(
                and_(
                    ConversationMessage.conversation_id == conversation_id,
                    ConversationMessage.is_delete == 0
                )
            )
        )
        max_index = result.scalar()
        return (max_index + 1) if max_index is not None else 0

    async def _save_prompt_lab_user_message(
        self,
        conversation_id: str,
        user_id: int,
        message_index: int,
        variant_index: int,
        content: str,
        image_urls: Optional[List[str]]
    ):
        """保存 Prompt Lab 变体的用户消息"""
        message = ConversationMessage(
            id=str(uuid.uuid4()),
            conversation_id=conversation_id,
            user_id=user_id,
            message_index=message_index,
            role="user",
            variant_index=variant_index,
            content=content,
            images=json.dumps(image_urls) if image_urls else None,
            is_delete=0
        )
        self.db.add(message)
        await self.db.commit()

    async def _stream_single_model(
        self,
        conversation_id: str,
        user_id: int,
        model_name: str,
        prompt: str,
        message_index: int,
        variant_index: Optional[int],
        image_urls: Optional[List[str]],
        web_search_enabled: bool,
        system_prompt: Optional[str] = None
    ) -> AsyncGenerator[str, None]:
        """
        调用单个模型并流式返回结果

        Args:
            conversation_id: 对话ID
            user_id: 用户ID
            model_name: 模型名称
            prompt: 提示词
            message_index: 消息索引
            variant_index: 变体索引（Prompt Lab模式）
            image_urls: 图片URL列表
            web_search_enabled: 是否启用联网搜索
            system_prompt: 系统提示词（代码模式使用）

        Yields:
            SSE事件
        """
        start_time = time.time()
        accumulated_content = ""
        accumulated_reasoning = ""
        input_tokens = CostCalculator.estimate_tokens(prompt)
        output_tokens = 0
        thinking_start_time = None
        model_info = None
        if not self.redis_client:
            async with AsyncSessionLocal() as info_db:
                model_info = await self._get_model_info(info_db, model_name)

        try:
            async with AsyncSessionLocal() as history_db:
                history_messages = await self._get_history_messages_for_context(
                    history_db,
                    conversation_id,
                    message_index,
                    variant_index
                )
            
            messages = []
            has_current_user_message = False
            
            for msg in history_messages:
                if msg.role == "user":
                    content = msg.content
                    
                    if variant_index is not None:
                        if msg.variant_index is not None and msg.variant_index != variant_index:
                            continue
                        elif msg.variant_index is None:
                            variant_prefix = f"变体{variant_index}:"
                            if not content.startswith(variant_prefix):
                                continue
                            content = content[len(variant_prefix):].strip()
                    
                    if msg.images:
                        try:
                            image_list = json.loads(msg.images) if isinstance(msg.images, str) else msg.images
                            if image_list:
                                content_parts = [{"type": "text", "text": content}]
                                for img_url in image_list:
                                    content_parts.append({
                                        "type": "image_url",
                                        "image_url": {"url": img_url}
                                    })
                                messages.append({"role": "user", "content": content_parts})
                            else:
                                messages.append({"role": "user", "content": content})
                        except:
                            messages.append({"role": "user", "content": content})
                    else:
                        messages.append({"role": "user", "content": content})
                    
                    if content == prompt or content.strip() == prompt.strip():
                        has_current_user_message = True
                
                elif msg.role == "assistant":
                    if variant_index is not None and msg.variant_index is not None:
                        if msg.variant_index != variant_index:
                            continue
                    
                    messages.append({"role": "assistant", "content": msg.content})
            
            if prompt and prompt.strip():
                if not has_current_user_message or not messages:
                    if image_urls and len(image_urls) > 0:
                        content_parts = [{"type": "text", "text": prompt}]
                        for image_url in image_urls:
                            content_parts.append({
                                "type": "image_url",
                                "image_url": {"url": image_url}
                            })
                        messages.append({"role": "user", "content": content_parts})
                    else:
                        messages.append({"role": "user", "content": prompt})
                    logger.info(f"📝 添加当前用户prompt（历史消息中不存在）: prompt={prompt}, imageUrls={image_urls}")
                elif image_urls and len(image_urls) > 0:
                    messages = [msg for msg in messages if not (msg["role"] == "user" and msg["content"] == prompt)]
                    content_parts = [{"type": "text", "text": prompt}]
                    for image_url in image_urls:
                        content_parts.append({
                            "type": "image_url",
                            "image_url": {"url": image_url}
                        })
                    messages.append({"role": "user", "content": content_parts})
                    logger.info(f"📝 替换当前用户prompt（使用传入的imageUrls）: prompt={prompt}, imageUrls={image_urls}")
                else:
                    logger.info(f"⏭️ 跳过添加当前prompt（历史消息中已包含，且无新图片）: prompt={prompt}")

            if system_prompt and system_prompt.strip():
                messages = [{"role": "system", "content": system_prompt.strip()}] + messages

            logger.info(f"🚀 开始流式调用模型: {model_name}, 上下文消息数: {len(messages)}, 当前prompt: {prompt}")
            
            stream = await self.openai_client.chat.completions.create(
                model=model_name,
                messages=messages,
                stream=True,
                temperature=0.7,
                timeout=60.0,
                extra_headers={
                    "HTTP-Referer": "https://codefather.cn",
                    "X-Title": "AI Evaluation Platform"
                }
            )
            
            chunk_count = 0
            reasoning_count = 0
            content_count = 0
            
            timeout_seconds = 30
            stream_total_timeout_seconds = 120
            stream_timeout = False
            
            stream_iter = stream.__aiter__()
            
            while True:
                if (time.time() - start_time) > stream_total_timeout_seconds:
                    logger.warning(f"⚠️  流式总时长超时: 对话ID={conversation_id}, 模型={model_name}, {stream_total_timeout_seconds}秒，强制结束")
                    stream_timeout = True
                    break
                try:
                    chunk = await asyncio.wait_for(stream_iter.__anext__(), timeout=timeout_seconds)
                except asyncio.TimeoutError:
                    logger.warning(f"⚠️  流式响应超时: 对话ID={conversation_id}, 模型={model_name}, {timeout_seconds}秒内未收到新数据，强制结束")
                    logger.info(f"📊 超时时统计: 总块数={chunk_count}, 思考块={reasoning_count}, 内容块={content_count}, 累积内容长度={len(accumulated_content)}")
                    stream_timeout = True
                    break
                except StopAsyncIteration:
                    break
                except Exception as e:
                    logger.error(f"❌ 流式迭代异常: {str(e)}", exc_info=True)
                    break
                chunk_count += 1
                
                if chunk_count % 5 == 0:
                    logger.debug(f"📊 处理chunk#{chunk_count}: 对话ID={conversation_id}, 模型={model_name}")
                
                if chunk.choices and len(chunk.choices) > 0:
                    delta = chunk.choices[0].delta
                    
                    if hasattr(delta, 'reasoning') and delta.reasoning:
                        reasoning_count += 1
                        reasoning_chunk = delta.reasoning
                        accumulated_reasoning += reasoning_chunk
                        elapsed_ms = int((time.time() - start_time) * 1000)
                        
                        if thinking_start_time is None:
                            thinking_start_time = time.time()
                            logger.info(f"💭 开始思考: 对话ID={conversation_id}, 模型={model_name}")
                        
                        reasoning_chunk_vo = StreamChunkVO(
                            conversation_id=conversation_id,
                            model_name=model_name,
                            variant_index=variant_index,
                            reasoning=accumulated_reasoning,
                            has_reasoning=True,
                            elapsed_ms=elapsed_ms,
                            done=False,
                            has_error=False
                        )
                        
                        event_data = f"data: {reasoning_chunk_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n"
                        yield event_data
                    
                    if delta.content:
                        content_count += 1
                        if content_count == 1 and reasoning_count > 0:
                            logger.info(f"📝 思考完成，开始输出内容: 对话ID={conversation_id}, 模型={model_name}, 思考块数={reasoning_count}")
                        elif content_count % 10 == 0:
                            logger.debug(f"📝 内容块#{content_count}: 对话ID={conversation_id}")
                            
                        content = delta.content
                        accumulated_content += content
                        output_tokens += CostCalculator.estimate_tokens(content)
                        elapsed_ms = int((time.time() - start_time) * 1000)
                        
                        chunk_vo = StreamChunkVO(
                            conversation_id=conversation_id,
                            model_name=model_name,
                            variant_index=variant_index,
                            content=content,
                            full_content=accumulated_content,
                            input_tokens=input_tokens,
                            output_tokens=output_tokens,
                            total_tokens=input_tokens + output_tokens,
                            elapsed_ms=elapsed_ms,
                            done=False,
                            has_error=False
                        )
                        
                        event_data = f"data: {chunk_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n"
                        yield event_data
            
            logger.info(f"🔄 流式循环结束: 对话ID={conversation_id}, 模型={model_name}, 总块数={chunk_count}, 思考块={reasoning_count}, 内容块={content_count}")
            
            response_time_ms = int((time.time() - start_time) * 1000)
            
            thinking_time = None
            if thinking_start_time is not None:
                thinking_time = int(time.time() - thinking_start_time)
            
            cost = Decimal('0')
            if self.redis_client:
                async def _fetch_pricing():
                    async with AsyncSessionLocal() as db:
                        m = await self._get_model_info(db, model_name)
                        return (m.input_price, m.output_price) if m else (None, None)
                input_price, output_price = await get_model_pricing_cached_async(
                    self.redis_client, model_name, _fetch_pricing
                )
                cost = CostCalculator.calculate_cost(
                    model_name, input_tokens, output_tokens, input_price, output_price
                )
            elif model_info:
                cost = CostCalculator.calculate_cost(
                    model_name,
                    input_tokens,
                    output_tokens,
                    model_info.input_price,
                    model_info.output_price
                )
            
            code_blocks_list = []
            if accumulated_content and accumulated_content.strip():
                code_blocks_list = extract_code_blocks(accumulated_content)
                if code_blocks_list:
                    logger.info("从响应中提取到 {} 个代码块", len(code_blocks_list))

            code_blocks_json = json.dumps(code_blocks_list, ensure_ascii=False) if code_blocks_list else None

            async with AsyncSessionLocal() as independent_db:
                await self._save_assistant_message(
                    independent_db,
                    conversation_id,
                    user_id,
                    message_index,
                    model_name,
                    variant_index,
                    accumulated_content,
                    input_tokens,
                    output_tokens,
                    cost,
                    response_time_ms,
                    accumulated_reasoning if accumulated_reasoning else None,
                    code_blocks_json
                )
                
                await self._update_conversation_stats(
                    independent_db,
                    conversation_id,
                    input_tokens + output_tokens,
                    cost
                )
            
            done_vo = StreamChunkVO(
                conversation_id=conversation_id,
                model_name=model_name,
                variant_index=variant_index,
                full_content=accumulated_content,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                total_tokens=input_tokens + output_tokens,
                cost=float(cost) if cost else None,
                response_time_ms=response_time_ms,
                done=True,
                has_error=False,
                message_index=message_index,
                reasoning=accumulated_reasoning if accumulated_reasoning else None,
                has_reasoning=bool(accumulated_reasoning),
                thinking_time=thinking_time,
                code_blocks=code_blocks_list if code_blocks_list else None,
                has_code_blocks=bool(code_blocks_list)
            )
            
            done_message = f"data: {done_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n"
            logger.info(f"📤 准备发送done消息: 对话ID={conversation_id}, 长度={len(done_message)}")
            yield done_message
            logger.info(f"✅ 流式响应完成: 对话ID={conversation_id}, 模型={model_name}, 耗时={response_time_ms}ms")
            
        except Exception as e:
            logger.error(f"❌ 流式响应异常: 对话ID={conversation_id}, 模型={model_name}, 错误={str(e)}", exc_info=True)
            error_vo = StreamChunkVO(
                conversation_id=conversation_id,
                model_name=model_name,
                variant_index=variant_index,
                error=str(e),
                has_error=True,
                done=True
            )
            yield f"data: {error_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n"
    
    async def _merge_streams(
        self,
        tasks: List[AsyncGenerator[str, None]],
        model_names: List[str]
    ) -> AsyncGenerator[str, None]:
        """
        合并多个流式响应
        
        Args:
            tasks: 异步生成器列表
            model_names: 与 tasks 一一对应的模型名称，用于错误 chunk 中携带 model_name
            
        Yields:
            合并后的SSE事件
        """
        queues = {i: asyncio.Queue() for i in range(len(tasks))}
        done_flags = {i: False for i in range(len(tasks))}
        
        async def consume_stream(idx: int, stream: AsyncGenerator[str, None], model_name: str):
            normal_exit = False
            try:
                async for event in stream:
                    await queues[idx].put(event)
                normal_exit = True
            except Exception as e:
                logger.error(f"流式响应异常 模型={model_name}: {e}", exc_info=True)
                error_vo = StreamChunkVO(
                    model_name=model_name,
                    error=str(e),
                    has_error=True,
                    done=True
                )
                await queues[idx].put(f"data: {error_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n")
            finally:
                if not normal_exit and not done_flags[idx]:
                    error_vo = StreamChunkVO(
                        model_name=model_name,
                        error="响应超时或已取消",
                        has_error=True,
                        done=True
                    )
                    await queues[idx].put(f"data: {error_vo.model_dump_json(by_alias=True, exclude_none=True)}\n\n")
                done_flags[idx] = True
                await queues[idx].put(None)
        
        model_list = model_names if len(model_names) >= len(tasks) else (model_names + [""] * (len(tasks) - len(model_names)))
        consumers = [
            asyncio.create_task(consume_stream(i, task, model_list[i]))
            for i, task in enumerate(tasks)
        ]
        merge_deadline = time.time() + 130
        
        while not all(done_flags.values()):
            if time.time() > merge_deadline:
                logger.warning("⚠️ 并排对比总时长超时(130秒)，取消未完成的模型")
                for i in range(len(consumers)):
                    if not done_flags[i]:
                        consumers[i].cancel()
                await asyncio.gather(*consumers, return_exceptions=True)
                for i in range(len(queues)):
                    try:
                        while True:
                            event = await asyncio.wait_for(queues[i].get(), timeout=1.0)
                            if event is None:
                                break
                            yield event
                    except asyncio.TimeoutError:
                        break
                break
            for idx, queue in queues.items():
                try:
                    event = await asyncio.wait_for(queue.get(), timeout=0.01)
                    if event is not None:
                        yield event
                except asyncio.TimeoutError:
                    continue
        
        await asyncio.gather(*consumers, return_exceptions=True)
    
    async def _save_user_message(
        self,
        conversation_id: str,
        user_id: int,
        content: str,
        image_urls: Optional[List[str]]
    ) -> int:
        """
        保存用户消息
        
        Args:
            conversation_id: 对话ID
            user_id: 用户ID
            content: 消息内容
            image_urls: 图片URL列表
            
        Returns:
            消息索引
        """
        result = await self.db.execute(
            select(func.max(ConversationMessage.message_index))
            .where(
                and_(
                    ConversationMessage.conversation_id == conversation_id,
                    ConversationMessage.is_delete == 0
                )
            )
        )
        max_index = result.scalar()
        message_index = (max_index + 1) if max_index is not None else 0
        
        message = ConversationMessage(
            id=str(uuid.uuid4()),
            conversation_id=conversation_id,
            user_id=user_id,
            message_index=message_index,
            role="user",
            content=content,
            images=json.dumps(image_urls) if image_urls else None,
            is_delete=0
        )
        
        self.db.add(message)
        await self.db.commit()
        
        return message_index
    
    async def _save_assistant_message(
        self,
        db: AsyncSession,
        conversation_id: str,
        user_id: int,
        message_index: int,
        model_name: str,
        variant_index: Optional[int],
        content: str,
        input_tokens: int,
        output_tokens: int,
        cost: Decimal,
        response_time_ms: int,
        reasoning: Optional[str] = None,
        code_blocks: Optional[str] = None
    ):
        """
        保存AI助手消息
        """
        message = ConversationMessage(
            id=str(uuid.uuid4()),
            conversation_id=conversation_id,
            user_id=user_id,
            message_index=message_index,
            role="assistant",
            model_name=model_name,
            variant_index=variant_index,
            content=content,
            reasoning=reasoning,
            code_blocks=code_blocks,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cost=cost,
            response_time_ms=response_time_ms,
            is_delete=0
        )
        
        db.add(message)
        await db.execute(
            update(Model)
            .where(Model.id == model_name, Model.is_delete == 0)
            .values(
                total_tokens=Model.total_tokens + (input_tokens + output_tokens),
                total_cost=Model.total_cost + cost
            )
        )
        await db.commit()

    async def _update_conversation_stats(
        self,
        db: AsyncSession,
        conversation_id: str,
        tokens: int,
        cost: Decimal
    ):
        """
        更新对话统计信息
        """
        result = await db.execute(
            select(Conversation).where(
                and_(
                    Conversation.id == conversation_id,
                    Conversation.is_delete == 0
                )
            )
        )
        conversation = result.scalar_one_or_none()
        
        if conversation:
            conversation.total_tokens = (conversation.total_tokens or 0) + tokens
            conversation.total_cost = (conversation.total_cost or Decimal('0')) + cost
            await db.commit()
    
    async def _get_model_info(self, db: AsyncSession, model_id: str) -> Optional[Model]:
        """
        获取模型信息
        """
        result = await db.execute(
            select(Model).where(
                and_(
                    Model.id == model_id,
                    Model.is_delete == 0
                )
            )
        )
        return result.scalar_one_or_none()
    
    async def _get_history_messages_for_context(
        self,
        db: AsyncSession,
        conversation_id: str,
        exclude_message_index: Optional[int],
        variant_index: Optional[int]
    ) -> List[ConversationMessage]:
        """
        获取用于上下文的历史消息
        
        Args:
            db: 数据库会话
            conversation_id: 对话ID
            exclude_message_index: 排除的消息索引（当前正在创建的消息）
            variant_index: 变体索引（用于Prompt Lab模式）
            
        Returns:
            历史消息列表
        """
        query = select(ConversationMessage).where(
            and_(
                ConversationMessage.conversation_id == conversation_id,
                ConversationMessage.is_delete == 0
            )
        )

        if variant_index is not None:
            query = query.where(
                or_(
                    ConversationMessage.variant_index == variant_index,
                    ConversationMessage.variant_index.is_(None)
                )
            )

        if exclude_message_index is not None:
            query = query.where(ConversationMessage.message_index < exclude_message_index)

        query = query.order_by(ConversationMessage.message_index.asc())

        result = await db.execute(query)
        messages = result.scalars().all()

        logger.info(f"📚 加载历史消息: 会话ID={conversation_id}, variantIndex={variant_index}, 数量={len(messages)}")
        
        return list(messages)
    
    def _generate_title(self, prompt: str) -> str:
        """
        生成对话标题
        """
        if len(prompt) > 20:
            return prompt[:20] + "..."
        return prompt
    
    async def get_conversation(
        self,
        conversation_id: str,
        user_id: int
    ) -> Optional[ConversationVO]:
        """
        获取对话详情
        """
        result = await self.db.execute(
            select(Conversation).where(
                and_(
                    Conversation.id == conversation_id,
                    Conversation.user_id == user_id,
                    Conversation.is_delete == 0
                )
            )
        )
        conversation = result.scalar_one_or_none()
        
        if not conversation:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在")
        
        if isinstance(conversation.models, str):
            conversation.models = json.loads(conversation.models)
        
        if isinstance(conversation.model_mapping, str) and conversation.model_mapping:
            conversation.model_mapping = json.loads(conversation.model_mapping)
        
        return ConversationVO.model_validate(conversation)
    
    async def delete_conversation(
        self,
        conversation_id: str,
        user_id: int
    ) -> bool:
        """
        删除对话（逻辑删除）
        """
        result = await self.db.execute(
            select(Conversation).where(
                and_(
                    Conversation.id == conversation_id,
                    Conversation.user_id == user_id,
                    Conversation.is_delete == 0
                )
            )
        )
        conversation = result.scalar_one_or_none()
        
        if not conversation:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在")
        
        conversation.is_delete = 1
        await self.db.commit()
        
        return True
    
    async def list_conversations(
        self,
        user_id: int,
        page_num: int = 1,
        page_size: int = 50,
        conversation_type: Optional[str] = None,
        code_preview_enabled: Optional[bool] = None
    ) -> tuple[List[dict], int]:
        """
        分页查询对话列表
        """
        query = select(Conversation).where(
            and_(
                Conversation.user_id == user_id,
                Conversation.is_delete == 0
            )
        )

        if conversation_type:
            query = query.where(Conversation.conversation_type == conversation_type)

        if code_preview_enabled is not None:
            query = query.where(
                Conversation.code_preview_enabled == (1 if code_preview_enabled else 0)
            )
        
        query = query.order_by(Conversation.update_time.desc())
        
        count_query = select(func.count()).select_from(query.subquery())
        total_result = await self.db.execute(count_query)
        total = total_result.scalar()
        
        offset = (page_num - 1) * page_size
        query = query.offset(offset).limit(page_size)
        
        result = await self.db.execute(query)
        conversations = result.scalars().all()
        
        conversation_list = []
        for conv in conversations:
            models = json.loads(conv.models) if isinstance(conv.models, str) else conv.models
            model_mapping = json.loads(conv.model_mapping) if isinstance(conv.model_mapping, str) and conv.model_mapping else conv.model_mapping
            
            conversation_list.append({
                "id": conv.id,
                "userId": conv.user_id,
                "title": conv.title,
                "conversationType": conv.conversation_type,
                "models": models,
                "codePreviewEnabled": conv.code_preview_enabled,
                "isAnonymous": conv.is_anonymous,
                "modelMapping": model_mapping,
                "totalTokens": conv.total_tokens,
                "totalCost": float(conv.total_cost) if conv.total_cost else 0.0,
                "createTime": conv.create_time.isoformat() if conv.create_time else None,
                "updateTime": conv.update_time.isoformat() if conv.update_time else None
            })
        
        return conversation_list, total
    
    async def get_conversation_messages(
        self,
        conversation_id: str,
        user_id: int
    ) -> List[Dict[str, Any]]:
        """
        获取对话消息列表
        """
        result = await self.db.execute(
            select(Conversation).where(
                and_(
                    Conversation.id == conversation_id,
                    Conversation.user_id == user_id,
                    Conversation.is_delete == 0
                )
            )
        )
        conversation = result.scalar_one_or_none()
        
        if not conversation:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在")
        
        messages_result = await self.db.execute(
            select(ConversationMessage).where(
                and_(
                    ConversationMessage.conversation_id == conversation_id,
                    ConversationMessage.is_delete == 0
                )
            ).order_by(ConversationMessage.message_index.asc())
        )
        messages = messages_result.scalars().all()
        
        message_list = []
        for msg in messages:
            images = json.loads(msg.images) if isinstance(msg.images, str) and msg.images and msg.images != 'null' else None
            tools_used = json.loads(msg.tools_used) if isinstance(msg.tools_used, str) and msg.tools_used else None
            
            message_list.append({
                "id": msg.id,
                "conversationId": msg.conversation_id,
                "userId": msg.user_id,
                "messageIndex": msg.message_index,
                "role": msg.role,
                "modelName": msg.model_name,
                "variantIndex": msg.variant_index,
                "content": msg.content,
                "images": images,
                "toolsUsed": tools_used,
                "responseTimeMs": msg.response_time_ms,
                "inputTokens": msg.input_tokens,
                "outputTokens": msg.output_tokens,
                "cost": float(msg.cost) if msg.cost else None,
                "reasoning": msg.reasoning,
                "codeBlocks": msg.code_blocks,
                "createTime": msg.create_time.isoformat() if msg.create_time else None
            })
        
        return message_list
