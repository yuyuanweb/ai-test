"""
模型数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import Column, String, BigInteger, DateTime, Integer, Text, DECIMAL
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Model(Base):
    """
    模型信息表模型（存储从OpenRouter同步的模型列表）
    """
    __tablename__ = 'model'

    id = Column(String(100), primary_key=True, comment='模型ID（OpenRouter格式，如：openai/gpt-4o）')
    name = Column(String(200), nullable=False, comment='模型显示名称')
    description = Column(Text, nullable=True, comment='模型描述')
    provider = Column(String(100), nullable=True, comment='提供商（如：OpenAI, Anthropic）')
    context_length = Column('contextLength', Integer, nullable=True, comment='上下文长度（tokens）')
    input_price = Column('inputPrice', DECIMAL(10, 6), nullable=True, comment='输入价格（每百万tokens，美元）')
    output_price = Column('outputPrice', DECIMAL(10, 6), nullable=True, comment='输出价格（每百万tokens，美元）')
    recommended = Column(Integer, nullable=False, default=0, comment='是否推荐（1-推荐 0-不推荐）')
    is_china = Column('isChina', Integer, nullable=False, default=0, comment='是否国内模型（1-国内 0-国外）')
    supports_multimodal = Column('supportsMultimodal', Integer, nullable=False, default=0, comment='是否支持多模态(图片)')
    supports_image_gen = Column('supportsImageGen', Integer, nullable=False, default=0, comment='是否支持图片生成')
    supports_tool_calling = Column('supportsToolCalling', Integer, nullable=False, default=0, comment='是否支持工具/函数调用（用于开启联网搜索等）')
    tags = Column(String(500), nullable=True, comment='标签（JSON数组字符串）')
    raw_data = Column('rawData', Text, nullable=True, comment='OpenRouter原始数据（JSON）')
    total_tokens = Column('totalTokens', BigInteger, nullable=False, default=0, comment='累计使用Token数')
    total_cost = Column('totalCost', DECIMAL(12, 6), nullable=False, default=Decimal('0'), comment='累计花费（美元）')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='逻辑删除')

    def __repr__(self):
        return f"<Model(id={self.id}, name={self.name}, provider={self.provider})>"
