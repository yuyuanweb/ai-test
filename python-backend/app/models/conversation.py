"""
对话数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import Column, String, BigInteger, DateTime, Integer, Text, DECIMAL, JSON
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Conversation(Base):
    """
    对话记录表模型
    """
    __tablename__ = 'conversation'

    id = Column(String(36), primary_key=True, comment='对话唯一标识')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    title = Column(String(200), nullable=True, comment='对话标题')
    conversation_type = Column('conversationType', String(20), nullable=False, comment='对话类型: side_by_side/prompt_lab/battle')
    models = Column(JSON, nullable=False, comment='参与的模型列表')
    code_preview_enabled = Column('codePreviewEnabled', Integer, nullable=False, default=0, comment='是否启用代码预览（1-启用 0-不启用）')
    is_anonymous = Column('isAnonymous', Integer, nullable=False, default=0, comment='是否为匿名模式（1-匿名 0-非匿名）')
    model_mapping = Column('modelMapping', JSON, nullable=True, comment='模型匿名映射关系')
    total_tokens = Column('totalTokens', Integer, nullable=True, default=0, comment='总Token消耗')
    total_cost = Column('totalCost', DECIMAL(10, 4), nullable=True, default=Decimal('0'), comment='总成本(USD)')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='逻辑删除')

    def __repr__(self):
        return f"<Conversation(id={self.id}, type={self.conversation_type}, user_id={self.user_id})>"
