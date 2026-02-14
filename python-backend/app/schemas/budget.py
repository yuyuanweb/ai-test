"""
预算相关请求和响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field, field_serializer


class BudgetUpdateRequest(BaseModel):
    """预算更新请求"""
    daily_budget: Optional[Decimal] = Field(None, description="日预算限额(USD)", alias="dailyBudget")
    monthly_budget: Optional[Decimal] = Field(None, description="月预算限额(USD)", alias="monthlyBudget")
    alert_threshold: Optional[int] = Field(None, description="预警阈值(%)", alias="alertThreshold")

    model_config = ConfigDict(populate_by_name=True)


class BudgetStatusVO(BaseModel):
    """
    预算状态视图对象
    """
    can_proceed: bool = Field(True, description="是否可以继续使用", alias="canProceed")
    status: str = Field("normal", description="预算状态：normal-正常，warning-预警，exceeded-超出")
    message: Optional[str] = Field("预算充足", description="提示消息")
    today_cost: Decimal = Field(Decimal("0"), description="今日已消耗", alias="todayCost")
    month_cost: Decimal = Field(Decimal("0"), description="本月已消耗", alias="monthCost")
    daily_budget: Optional[Decimal] = Field(None, description="日预算限额", alias="dailyBudget")
    monthly_budget: Optional[Decimal] = Field(None, description="月预算限额", alias="monthlyBudget")
    daily_usage_percent: Decimal = Field(Decimal("0"), description="日预算使用百分比", alias="dailyUsagePercent")
    monthly_usage_percent: Decimal = Field(Decimal("0"), description="月预算使用百分比", alias="monthlyUsagePercent")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    @field_serializer(
        "today_cost", "month_cost", "daily_budget", "monthly_budget",
        "daily_usage_percent", "monthly_usage_percent",
        when_used="json-unless-none"
    )
    def _serialize_decimal(self, v: Optional[Decimal]) -> Optional[float]:
        return float(v) if v is not None else None

    @classmethod
    def normal(
        cls,
        today_cost: Decimal,
        month_cost: Decimal,
        daily_budget: Optional[Decimal],
        monthly_budget: Optional[Decimal]
    ) -> "BudgetStatusVO":
        vo = cls(
            can_proceed=True,
            status="normal",
            message="预算充足",
            today_cost=today_cost,
            month_cost=month_cost,
            daily_budget=daily_budget,
            monthly_budget=monthly_budget
        )
        vo._calculate_usage_percent(today_cost, month_cost, daily_budget, monthly_budget)
        return vo

    @classmethod
    def warning(
        cls,
        message: str,
        today_cost: Decimal,
        month_cost: Decimal,
        daily_budget: Optional[Decimal],
        monthly_budget: Optional[Decimal],
        daily_usage_percent: Optional[Decimal] = None,
        monthly_usage_percent: Optional[Decimal] = None
    ) -> "BudgetStatusVO":
        vo = cls(
            can_proceed=True,
            status="warning",
            message=message,
            today_cost=today_cost,
            month_cost=month_cost,
            daily_budget=daily_budget,
            monthly_budget=monthly_budget
        )
        vo._calculate_usage_percent(today_cost, month_cost, daily_budget, monthly_budget)
        return vo

    @classmethod
    def exceeded(
        cls,
        message: str,
        today_cost: Decimal,
        month_cost: Decimal,
        daily_budget: Optional[Decimal],
        monthly_budget: Optional[Decimal],
        daily_usage_percent: Optional[Decimal] = None,
        monthly_usage_percent: Optional[Decimal] = None
    ) -> "BudgetStatusVO":
        vo = cls(
            can_proceed=False,
            status="exceeded",
            message=message,
            today_cost=today_cost,
            month_cost=month_cost,
            daily_budget=daily_budget,
            monthly_budget=monthly_budget
        )
        vo._calculate_usage_percent(today_cost, month_cost, daily_budget, monthly_budget)
        return vo

    def _calculate_usage_percent(
        self,
        today_cost: Decimal,
        month_cost: Decimal,
        daily_budget: Optional[Decimal],
        monthly_budget: Optional[Decimal]
    ) -> None:
        if daily_budget and daily_budget > 0:
            self.daily_usage_percent = (today_cost * 100 / daily_budget).quantize(Decimal("0.01"))
        else:
            self.daily_usage_percent = Decimal("0")
        if monthly_budget and monthly_budget > 0:
            self.monthly_usage_percent = (month_cost * 100 / monthly_budget).quantize(Decimal("0.01"))
        else:
            self.monthly_usage_percent = Decimal("0")
