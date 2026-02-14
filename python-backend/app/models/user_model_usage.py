"""
用户-模型使用统计数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import Column, String, BigInteger, DateTime, Integer, DECIMAL
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class UserModelUsage(Base):
    """
    用户-模型使用统计表模型
    """
    __tablename__ = 'user_model_usage'

    id = Column(String(36), primary_key=True, comment='记录唯一标识')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    model_name = Column('modelName', String(100), nullable=False, comment='模型名称')
    total_tokens = Column('totalTokens', BigInteger, nullable=False, default=0, comment='累计使用Token数')
    total_cost = Column('totalCost', DECIMAL(12, 6), nullable=False, default=Decimal('0'), comment='累计花费（美元）')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='逻辑删除')

    def __repr__(self):
        return f"<UserModelUsage(user_id={self.user_id}, model_name={self.model_name})>"
