"""
评分数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from sqlalchemy import Column, String, BigInteger, DateTime, Integer
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Rating(Base):
    """
    用户评分表模型
    """
    __tablename__ = 'rating'

    id = Column(String(36), primary_key=True, comment='评分唯一标识')
    conversation_id = Column('conversationId', String(36), nullable=False, comment='对话ID')
    message_index = Column('messageIndex', Integer, nullable=False, comment='消息序号(对应某一轮对话)')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    rating_type = Column('ratingType', String(20), nullable=False, comment='评分类型: left_better/right_better/tie/both_bad/variant_N')
    winner_model = Column('winnerModel', String(100), nullable=True, comment='获胜模型')
    loser_model = Column('loserModel', String(100), nullable=True, comment='失败模型')
    winner_variant_index = Column('winnerVariantIndex', Integer, nullable=True, comment='获胜变体索引(用于prompt_lab)')
    loser_variant_index = Column('loserVariantIndex', Integer, nullable=True, comment='失败变体索引(用于prompt_lab)')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='逻辑删除')

    def __repr__(self):
        return f"<Rating(id={self.id}, conversation_id={self.conversation_id}, rating_type={self.rating_type})>"
