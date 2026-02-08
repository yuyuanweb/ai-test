"""
评分服务层
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import uuid
from typing import Optional, List
from sqlalchemy import select, and_, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.rating import Rating
from app.schemas.rating import RatingVO
from app.core.errors import BusinessException, ErrorCode


class RatingService:
    """
    评分服务类
    """
    
    def __init__(self, db: AsyncSession):
        self.db = db
    
    async def save_or_update_rating(
        self,
        conversation_id: str,
        message_index: int,
        user_id: int,
        rating_type: str,
        winner_model: Optional[str] = None,
        loser_model: Optional[str] = None,
        winner_variant_index: Optional[int] = None,
        loser_variant_index: Optional[int] = None
    ) -> bool:
        """
        保存或更新评分
        
        Args:
            conversation_id: 对话ID
            message_index: 消息序号
            user_id: 用户ID
            rating_type: 评分类型
            winner_model: 获胜模型
            loser_model: 失败模型
            winner_variant_index: 获胜变体索引
            loser_variant_index: 失败变体索引
            
        Returns:
            是否成功
        """
        result = await self.db.execute(
            select(Rating).where(
                and_(
                    Rating.conversation_id == conversation_id,
                    Rating.message_index == message_index,
                    Rating.user_id == user_id,
                    Rating.is_delete == 0
                )
            )
        )
        existing_rating = result.scalar_one_or_none()
        
        if existing_rating:
            existing_rating.rating_type = rating_type
            existing_rating.winner_model = winner_model
            existing_rating.loser_model = loser_model
            existing_rating.winner_variant_index = winner_variant_index
            existing_rating.loser_variant_index = loser_variant_index
        else:
            new_rating = Rating(
                id=str(uuid.uuid4()),
                conversation_id=conversation_id,
                message_index=message_index,
                user_id=user_id,
                rating_type=rating_type,
                winner_model=winner_model,
                loser_model=loser_model,
                winner_variant_index=winner_variant_index,
                loser_variant_index=loser_variant_index,
                is_delete=0
            )
            self.db.add(new_rating)
        
        await self.db.commit()
        return True
    
    async def get_rating(
        self,
        conversation_id: str,
        message_index: int,
        user_id: int
    ) -> Optional[RatingVO]:
        """
        获取评分
        
        Args:
            conversation_id: 对话ID
            message_index: 消息序号
            user_id: 用户ID
            
        Returns:
            评分VO
        """
        result = await self.db.execute(
            select(Rating).where(
                and_(
                    Rating.conversation_id == conversation_id,
                    Rating.message_index == message_index,
                    Rating.user_id == user_id,
                    Rating.is_delete == 0
                )
            )
        )
        rating = result.scalar_one_or_none()
        
        if not rating:
            return None
        
        return RatingVO.model_validate(rating)
    
    async def get_ratings_by_conversation(
        self,
        conversation_id: str,
        user_id: int
    ) -> List[RatingVO]:
        """
        获取整个会话的所有评分
        
        Args:
            conversation_id: 对话ID
            user_id: 用户ID
            
        Returns:
            评分VO列表
        """
        result = await self.db.execute(
            select(Rating).where(
                and_(
                    Rating.conversation_id == conversation_id,
                    Rating.user_id == user_id,
                    Rating.is_delete == 0
                )
            ).order_by(Rating.message_index)
        )
        ratings = result.scalars().all()
        
        return [RatingVO.model_validate(rating) for rating in ratings]
    
    async def delete_rating(
        self,
        conversation_id: str,
        message_index: int,
        user_id: int
    ) -> bool:
        """
        删除评分（逻辑删除）
        
        Args:
            conversation_id: 对话ID
            message_index: 消息序号
            user_id: 用户ID
            
        Returns:
            是否成功
        """
        result = await self.db.execute(
            select(Rating).where(
                and_(
                    Rating.conversation_id == conversation_id,
                    Rating.message_index == message_index,
                    Rating.user_id == user_id,
                    Rating.is_delete == 0
                )
            )
        )
        rating = result.scalar_one_or_none()
        
        if not rating:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "评分不存在")
        
        rating.is_delete = 1
        await self.db.commit()
        
        return True
