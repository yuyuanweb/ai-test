"""
场景数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from sqlalchemy import Column, String, BigInteger, Text, Integer, DateTime
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class Scene(Base):
    """
    场景表模型
    """
    __tablename__ = 'scene'

    id = Column(String(36), primary_key=True, comment='场景唯一标识')
    user_id = Column('userId', BigInteger, nullable=True, comment='创建用户ID(预设场景为NULL)')
    name = Column('name', String(100), nullable=False, comment='场景名称')
    description = Column('description', Text, nullable=True, comment='场景描述')
    category = Column('category', String(50), nullable=True, comment='分类:编程/数学/文案等')
    is_preset = Column('isPreset', Integer, nullable=False, default=0, comment='是否为预设场景(1-预设 0-自定义)')
    is_active = Column('isActive', Integer, nullable=False, default=1, comment='是否启用(1-启用 0-禁用)')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='是否删除')

    def __repr__(self):
        return f"<Scene(id={self.id}, name={self.name})>"
