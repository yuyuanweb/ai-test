"""
提示词模板数据库模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from datetime import datetime
from sqlalchemy import Column, String, BigInteger, Text, Integer, DateTime
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class PromptTemplate(Base):
    """
    提示词模板表模型
    """
    __tablename__ = "prompt_template"

    id = Column(String(36), primary_key=True, comment="模板唯一标识")
    user_id = Column("userId", BigInteger, nullable=True, comment="用户ID(预设模板为NULL)")
    name = Column("name", String(100), nullable=False, comment="模板名称")
    description = Column("description", Text, nullable=True, comment="模板描述")
    strategy = Column("strategy", String(50), nullable=False, comment="策略类型: direct/cot/role_play/few_shot")
    content = Column("content", Text, nullable=False, comment="模板内容(支持占位符)")
    variables = Column("variables", String(500), nullable=True, comment="变量列表(JSON数组)")
    category = Column("category", String(50), nullable=True, comment="分类")
    is_preset = Column("isPreset", Integer, nullable=False, default=0, comment="是否为预设模板(1-预设 0-自定义)")
    usage_count = Column("usageCount", Integer, nullable=False, default=0, comment="使用次数")
    is_active = Column("isActive", Integer, nullable=False, default=1, comment="是否启用(1-启用 0-禁用)")
    create_time = Column("createTime", DateTime, nullable=False, default=datetime.now, comment="创建时间")
    update_time = Column("updateTime", DateTime, nullable=False, default=datetime.now, onupdate=datetime.now, comment="更新时间")
    is_delete = Column("isDelete", Integer, nullable=False, default=0, comment="是否删除")

    def __repr__(self):
        return f"<PromptTemplate(id={self.id}, name={self.name})>"
