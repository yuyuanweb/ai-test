// Package service 评分服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"time"

	"gorm.io/gorm"
)

type RatingService struct {
	ratingRepo *repository.RatingRepository
}

func NewRatingService(ratingRepo *repository.RatingRepository) *RatingService {
	return &RatingService{
		ratingRepo: ratingRepo,
	}
}

func (s *RatingService) SaveOrUpdateRating(req *dto.RatingAddRequest, userID int64) error {
	if req.ConversationID == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}
	if req.RatingType == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "评分类型不能为空")
	}

	messageIndex := 0
	if req.MessageIndex != nil {
		messageIndex = *req.MessageIndex
	}

	rating := &model.Rating{
		ID:                 utils.GenerateUUID(),
		ConversationID:     req.ConversationID,
		MessageIndex:       messageIndex,
		UserID:             userID,
		RatingType:         req.RatingType,
		WinnerModel:        req.WinnerModel,
		LoserModel:         req.LoserModel,
		WinnerVariantIndex: req.WinnerVariantIndex,
		LoserVariantIndex:  req.LoserVariantIndex,
		CreateTime:         time.Now(),
		UpdateTime:         time.Now(),
		IsDelete:           0,
	}

	if err := s.ratingRepo.SaveOrUpdate(rating); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "保存评分失败")
	}

	return nil
}

func (s *RatingService) GetRating(conversationID string, messageIndex int, userID int64) (*vo.RatingVO, error) {
	if conversationID == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}

	rating, err := s.ratingRepo.FindByConversationAndMessage(conversationID, messageIndex, userID)
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询评分失败")
	}

	return s.convertToVO(rating), nil
}

func (s *RatingService) GetRatingsByConversationID(conversationID string, userID int64) ([]vo.RatingVO, error) {
	if conversationID == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}

	ratings, err := s.ratingRepo.FindByConversation(conversationID, userID)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询评分列表失败")
	}

	result := make([]vo.RatingVO, 0, len(ratings))
	for _, rating := range ratings {
		result = append(result, *s.convertToVO(&rating))
	}

	return result, nil
}

func (s *RatingService) DeleteRating(conversationID string, messageIndex int, userID int64) error {
	if conversationID == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}

	if err := s.ratingRepo.Delete(conversationID, messageIndex, userID); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "删除评分失败")
	}

	return nil
}

func (s *RatingService) convertToVO(rating *model.Rating) *vo.RatingVO {
	return &vo.RatingVO{
		ID:                 rating.ID,
		ConversationID:     rating.ConversationID,
		MessageIndex:       rating.MessageIndex,
		UserID:             rating.UserID,
		RatingType:         rating.RatingType,
		WinnerModel:        rating.WinnerModel,
		LoserModel:         rating.LoserModel,
		WinnerVariantIndex: rating.WinnerVariantIndex,
		LoserVariantIndex:  rating.LoserVariantIndex,
		CreateTime:         rating.CreateTime,
		UpdateTime:         rating.UpdateTime,
	}
}
