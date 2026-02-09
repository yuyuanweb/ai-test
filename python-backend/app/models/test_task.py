"""
批量测试任务数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from sqlalchemy import Column, String, BigInteger, Integer, DateTime, Text
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class TestTask(Base):
    """
    批量测试任务表模型
    """
    __tablename__ = 'test_task'

    id = Column(String(36), primary_key=True, comment='任务唯一标识')
    user_id = Column('userId', BigInteger, nullable=False, comment='用户ID')
    name = Column('name', String(200), nullable=True, comment='任务名称')
    scene_id = Column('sceneId', String(36), nullable=False, comment='场景ID')
    models = Column('models', Text, nullable=False, comment='测试的模型列表(JSON)')
    config = Column('config', Text, nullable=True, comment='任务配置参数(JSON)')
    status = Column('status', String(20), nullable=False, comment='状态: pending/running/completed/failed/cancelled')
    total_subtasks = Column('totalSubtasks', Integer, nullable=False, default=0, comment='子任务总数')
    completed_subtasks = Column('completedSubtasks', Integer, nullable=False, default=0, comment='已完成子任务数')
    started_at = Column('startedAt', DateTime, nullable=True, comment='开始时间')
    completed_at = Column('completedAt', DateTime, nullable=True, comment='完成时间')
    create_time = Column('createTime', DateTime, nullable=False, default=datetime.now, comment='创建时间')
    update_time = Column('updateTime', DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment='更新时间')
    is_delete = Column('isDelete', Integer, nullable=False, default=0, comment='是否删除')

    def __repr__(self):
        return f"<TestTask(id={self.id}, status={self.status})>"
