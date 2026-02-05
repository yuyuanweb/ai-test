"""
OpenRouter配置
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from langchain_openai import ChatOpenAI
from app.core.config import get_settings

settings = get_settings()


def get_openrouter_client(model_name: str = "deepseek/deepseek-chat") -> ChatOpenAI:
    """
    获取OpenRouter客户端
    
    Args:
        model_name: 模型名称，默认使用DeepSeek
        
    Returns:
        ChatOpenAI客户端实例
    """
    return ChatOpenAI(
        model=model_name,
        openai_api_key=settings.OPENROUTER_API_KEY,
        openai_api_base="https://openrouter.ai/api/v1",
        temperature=0.7,
        max_tokens=2000,
        streaming=True,
        model_kwargs={
            "extra_headers": {
                "HTTP-Referer": "https://codefather.cn",
                "X-Title": "AI Evaluation Platform"
            }
        }
    )


SUPPORTED_MODELS = [
    "deepseek/deepseek-chat",
    "openai/gpt-4o",
    "openai/gpt-4o-mini",
    "anthropic/claude-3.5-sonnet",
    "anthropic/claude-3-opus",
    "google/gemini-pro-1.5",
    "meta-llama/llama-3.1-70b-instruct",
    "qwen/qwen-2.5-72b-instruct"
]
