"""
业务异常定义
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from enum import Enum
from fastapi import HTTPException, status


class ErrorCode(Enum):
    """
    错误码枚举
    """
    SUCCESS = (0, "ok")
    PARAMS_ERROR = (40000, "请求参数错误")
    NOT_LOGIN_ERROR = (40100, "未登录")
    NO_AUTH_ERROR = (40101, "无权限")
    NOT_FOUND_ERROR = (40400, "请求数据不存在")
    FORBIDDEN_ERROR = (40300, "禁止访问")
    SYSTEM_ERROR = (50000, "系统内部异常")
    OPERATION_ERROR = (50001, "操作失败")

    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message


class BusinessException(HTTPException):
    """
    业务异常类
    """
    def __init__(self, error_code: ErrorCode, detail: str = None):
        self.error_code = error_code
        message = detail if detail else error_code.message
        super().__init__(
            status_code=status.HTTP_200_OK,
            detail=message
        )
        self.code = error_code.code

    def to_dict(self):
        """
        转换为字典格式
        """
        return {
            "code": self.code,
            "data": None,
            "message": self.detail
        }
