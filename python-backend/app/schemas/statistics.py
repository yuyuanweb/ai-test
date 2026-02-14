"""
统计相关响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from decimal import Decimal
from typing import Optional, List
from pydantic import BaseModel, ConfigDict, Field, field_serializer


class ModelCostVO(BaseModel):
    """按模型分类的成本"""
    model_name: str = Field(..., description="模型名称", alias="modelName")
    cost: Decimal = Field(..., description="成本(USD)")
    percentage: Decimal = Field(Decimal("0"), description="占比(%)")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True, protected_namespaces=())

    @field_serializer("cost", "percentage", when_used="json-unless-none")
    def _serialize_decimal(self, v: Decimal) -> float:
        return float(v)


class DailyCostVO(BaseModel):
    """日成本"""
    date: str = Field(..., description="日期")
    cost: Decimal = Field(Decimal("0"), description="成本(USD)")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    @field_serializer("cost", when_used="json-unless-none")
    def _serialize_cost(self, v: Decimal) -> float:
        return float(v)


class CostStatisticsVO(BaseModel):
    """成本统计数据"""
    total_cost: Decimal = Field(Decimal("0"), description="总成本", alias="totalCost")
    today_cost: Decimal = Field(Decimal("0"), description="今日成本", alias="todayCost")
    week_cost: Optional[Decimal] = Field(None, description="本周成本", alias="weekCost")
    month_cost: Decimal = Field(Decimal("0"), description="本月成本", alias="monthCost")
    cost_by_model: List[ModelCostVO] = Field(default_factory=list, description="按模型分类", alias="costByModel")
    cost_trend: List[DailyCostVO] = Field(default_factory=list, description="成本趋势", alias="costTrend")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    @field_serializer(
        "total_cost", "today_cost", "week_cost", "month_cost",
        when_used="json-unless-none"
    )
    def _serialize_decimal(self, v: Optional[Decimal]) -> Optional[float]:
        return float(v) if v is not None else None


class ModelUsageVO(BaseModel):
    """按模型分类的使用量"""
    model_name: str = Field(..., description="模型名称", alias="modelName")
    call_count: int = Field(0, description="调用次数", alias="callCount")
    tokens: int = Field(0, description="Token数")
    percentage: float = Field(0.0, description="占比(%)")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True, protected_namespaces=())


class DailyUsageVO(BaseModel):
    """日使用量"""
    date: str = Field(..., description="日期")
    api_calls: int = Field(0, description="API调用次数", alias="apiCalls")
    tokens: int = Field(0, description="Token数")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class UsageStatisticsVO(BaseModel):
    """使用量统计数据"""
    total_api_calls: int = Field(0, description="总API调用次数", alias="totalApiCalls")
    today_api_calls: int = Field(0, description="今日API调用次数", alias="todayApiCalls")
    total_tokens: int = Field(0, description="总Token", alias="totalTokens")
    total_input_tokens: int = Field(0, description="总输入Token", alias="totalInputTokens")
    total_output_tokens: int = Field(0, description="总输出Token", alias="totalOutputTokens")
    today_tokens: int = Field(0, description="今日Token", alias="todayTokens")
    usage_by_model: List[ModelUsageVO] = Field(default_factory=list, alias="usageByModel")
    usage_trend: List[DailyUsageVO] = Field(default_factory=list, alias="usageTrend")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class ModelPerformanceVO(BaseModel):
    """按模型分类的性能"""
    model_name: str = Field(..., description="模型名称", alias="modelName")
    call_count: int = Field(0, description="调用次数", alias="callCount")
    avg_response_time: float = Field(0.0, description="平均响应时间(ms)", alias="avgResponseTime")
    min_response_time: int = Field(0, description="最小响应时间(ms)", alias="minResponseTime")
    max_response_time: int = Field(0, description="最大响应时间(ms)", alias="maxResponseTime")
    avg_input_tokens: float = Field(0.0, description="平均输入Token", alias="avgInputTokens")
    avg_output_tokens: float = Field(0.0, description="平均输出Token", alias="avgOutputTokens")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True, protected_namespaces=())


class PerformanceStatisticsVO(BaseModel):
    """性能统计数据"""
    avg_response_time: float = Field(0.0, description="平均响应时间(ms)", alias="avgResponseTime")
    min_response_time: int = Field(0, description="最小响应时间(ms)", alias="minResponseTime")
    max_response_time: int = Field(0, description="最大响应时间(ms)", alias="maxResponseTime")
    performance_by_model: List[ModelPerformanceVO] = Field(default_factory=list, alias="performanceByModel")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class RealtimeCostVO(BaseModel):
    """实时成本监控"""
    today_cost: Decimal = Field(Decimal("0"), description="今日消耗", alias="todayCost")
    month_cost: Decimal = Field(Decimal("0"), description="本月消耗", alias="monthCost")
    today_tokens: int = Field(0, description="今日Token", alias="todayTokens")
    today_api_calls: int = Field(0, description="今日API调用", alias="todayApiCalls")
    avg_cost_per_call: Decimal = Field(Decimal("0"), description="平均成本/次", alias="avgCostPerCall")
    daily_budget: Optional[Decimal] = Field(None, description="日预算", alias="dailyBudget")
    monthly_budget: Optional[Decimal] = Field(None, description="月预算", alias="monthlyBudget")
    daily_usage_percent: Decimal = Field(Decimal("0"), description="日预算使用%", alias="dailyUsagePercent")
    monthly_usage_percent: Decimal = Field(Decimal("0"), description="月预算使用%", alias="monthlyUsagePercent")
    budget_status: str = Field("normal", description="预算状态", alias="budgetStatus")
    budget_message: str = Field("预算充足", description="预算提示", alias="budgetMessage")
    alert_threshold: int = Field(80, description="预警阈值%", alias="alertThreshold")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    @field_serializer(
        "today_cost", "month_cost", "avg_cost_per_call", "daily_budget",
        "monthly_budget", "daily_usage_percent", "monthly_usage_percent",
        when_used="json-unless-none"
    )
    def _serialize_decimal(self, v: Optional[Decimal]) -> Optional[float]:
        return float(v) if v is not None else None
