"""
密码加密工具
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import hashlib


SALT = "yupi"


def encrypt_password(password: str) -> str:
    """
    密码加密(MD5 + 盐值)
    
    Args:
        password: 原始密码
        
    Returns:
        加密后的密码
    """
    salted_password = password + SALT
    return hashlib.md5(salted_password.encode('utf-8')).hexdigest()
