// Package model 模型信息实体
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import "time"

type Model struct {
	ID                  string    `gorm:"column:id;type:varchar(100);primaryKey" json:"id"`
	Name                string    `gorm:"column:name;type:varchar(255);not null" json:"name"`
	Description         string    `gorm:"column:description;type:text" json:"description"`
	Provider            string    `gorm:"column:provider;type:varchar(100)" json:"provider"`
	ContextLength       int       `gorm:"column:contextLength;type:int" json:"contextLength"`
	InputPrice          float64   `gorm:"column:inputPrice;type:decimal(12,8)" json:"inputPrice"`
	OutputPrice         float64   `gorm:"column:outputPrice;type:decimal(12,8)" json:"outputPrice"`
	Recommended         int       `gorm:"column:recommended;type:tinyint;default:0" json:"recommended"`
	IsChina             int       `gorm:"column:isChina;type:tinyint;default:0" json:"isChina"`
	SupportsMultimodal  int       `gorm:"column:supportsMultimodal;type:tinyint;default:0" json:"supportsMultimodal"`
	SupportsImageGen    int       `gorm:"column:supportsImageGen;type:tinyint;default:0" json:"supportsImageGen"`
	SupportsToolCalling int       `gorm:"column:supportsToolCalling;type:tinyint;default:0" json:"supportsToolCalling"`
	Tags                string    `gorm:"column:tags;type:json" json:"tags"`
	RawData             string    `gorm:"column:rawData;type:json" json:"rawData"`
	TotalTokens         int64     `gorm:"column:totalTokens;type:bigint;default:0" json:"totalTokens"`
	TotalCost           float64   `gorm:"column:totalCost;type:decimal(12,6);default:0" json:"totalCost"`
	CreateTime          time.Time `gorm:"column:createTime;type:datetime" json:"createTime"`
	UpdateTime          time.Time `gorm:"column:updateTime;type:datetime" json:"updateTime"`
	IsDelete            int       `gorm:"column:isDelete;type:tinyint;default:0" json:"isDelete"`
}

func (Model) TableName() string {
	return "model"
}
