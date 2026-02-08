"""
对话消息数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import Column, String, BigInteger, DateTime, Integer, Text, DECIMAL, JSON
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class ConversationMessage(Base):
    """
    对话消息表模型
    """
    __tablename__ = 'conversation_message'

    id = Column(String(36), primary_key=True, comment='消息唯一标识')
    conversation_id = Column('conversationId', String(36), nullable=False, comment='对话ID')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    message_index = Column('messageIndex', Integer, nullable=False, comment='消息序号(从0开始)')
    role = Column(String(20), nullable=False, comment='角色: user/assistant')
    model_name = Column('modelName', String(100), nullable=True, comment='模型名称(assistant消息)')
    variant_index = Column('variantIndex', Integer, nullable=True, comment='变体索引(用于prompt_lab，user和assistant消息)')
    content = Column(Text, nullable=False, comment='消息内容')
    images = Column(JSON, nullable=True, comment='图片URL列表')
    tools_used = Column('toolsUsed', JSON, nullable=True, comment='工具调用信息（JSON，含联网搜索关键词/来源等）')
    response_time_ms = Column('responseTimeMs', Integer, nullable=True, comment='响应时间(毫秒)')
    input_tokens = Column('inputTokens', Integer, nullable=True, comment='输入Token数')
    output_tokens = Column('outputTokens', Integer, nullable=True, comment='输出Token数')
    cost = Column(DECIMAL(10, 6), nullable=True, comment='成本(USD)')
    reasoning = Column(Text, nullable=True, comment='思考过程（thinking模式）')
    code_blocks = Column('codeBlocks', Text, nullable=True, comment='代码块列表（JSON格式）')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='逻辑删除')

    def __repr__(self):
        return f"<ConversationMessage(id={self.id}, conversation_id={self.conversation_id}, role={self.role})>"
