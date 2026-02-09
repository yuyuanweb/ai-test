"""
批量测试结果数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from decimal import Decimal
from sqlalchemy import Column, String, BigInteger, Integer, DateTime, Text, DECIMAL
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class TestResult(Base):
    """
    批量测试结果表模型
    """
    __tablename__ = 'test_result'

    id = Column(String(36), primary_key=True, comment='结果唯一标识')
    task_id = Column('taskId', String(36), nullable=False, comment='任务ID')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    scene_id = Column('sceneId', String(36), nullable=False, comment='场景ID')
    prompt_id = Column('promptId', String(36), nullable=False, comment='提示词ID')
    model_name = Column('modelName', String(100), nullable=False, comment='模型名称')
    input_prompt = Column('inputPrompt', Text, nullable=False, comment='输入提示词')
    output_text = Column('outputText', Text, nullable=False, comment='输出内容')
    reasoning = Column('reasoning', Text, nullable=True, comment='思考过程内容')
    response_time_ms = Column('responseTimeMs', Integer, nullable=True, comment='响应时间(毫秒)')
    input_tokens = Column('inputTokens', Integer, nullable=True, comment='输入Token数')
    output_tokens = Column('outputTokens', Integer, nullable=True, comment='输出Token数')
    cost = Column('cost', DECIMAL(10, 6), nullable=True, comment='成本(USD)')
    user_rating = Column('userRating', Integer, nullable=True, comment='用户评分(1-5)')
    ai_score = Column('aiScore', Text, nullable=True, comment='AI评分详情(JSON)')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='是否删除')

    def __repr__(self):
        return f"<TestResult(id={self.id}, model={self.model_name})>"
