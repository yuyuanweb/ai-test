"""
场景提示词数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from sqlalchemy import Column, String, BigInteger, Text, Integer, DateTime
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class ScenePrompt(Base):
    """
    场景提示词表模型
    """
    __tablename__ = 'scene_prompt'

    id = Column(String(36), primary_key=True, comment='提示词唯一标识')
    scene_id = Column('sceneId', String(36), nullable=False, comment='场景ID')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    prompt_index = Column('promptIndex', Integer, nullable=False, comment='提示词序号')
    title = Column('title', String(200), nullable=False, comment='提示词标题')
    content = Column('content', Text, nullable=False, comment='提示词内容')
    difficulty = Column('difficulty', String(20), nullable=True, comment='难度: easy/medium/hard')
    tags = Column('tags', String(500), nullable=True, comment='标签数组(JSON)')
    expected_output = Column('expectedOutput', Text, nullable=True, comment='期望输出(可选)')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='是否删除')

    def __repr__(self):
        return f"<ScenePrompt(id={self.id}, title={self.title})>"
