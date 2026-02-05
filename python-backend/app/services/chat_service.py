"""
对话服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import AsyncIterator
from langchain_core.messages import HumanMessage
from app.core.openrouter_config import get_openrouter_client


class ChatService:
    """
    对话服务类
    """
    
    @staticmethod
    async def stream_chat(
        message: str,
        model_name: str = "deepseek/deepseek-chat"
    ) -> AsyncIterator[str]:
        """
        流式对话
        
        Args:
            message: 用户消息
            model_name: 模型名称
            
        Yields:
            流式返回的文本块
        """
        client = get_openrouter_client(model_name)
        
        messages = [HumanMessage(content=message)]
        
        async for chunk in client.astream(messages):
            if chunk.content:
                yield chunk.content
    
    @staticmethod
    async def simple_chat(
        message: str,
        model_name: str = "deepseek/deepseek-chat"
    ) -> str:
        """
        简单对话(非流式)
        
        Args:
            message: 用户消息
            model_name: 模型名称
            
        Returns:
            模型回复
        """
        client = get_openrouter_client(model_name)
        
        messages = [HumanMessage(content=message)]
        
        response = await client.ainvoke(messages)
        
        return response.content
