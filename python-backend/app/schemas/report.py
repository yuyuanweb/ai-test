"""
报告相关响应模型
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from typing import Optional, List
from pydantic import BaseModel, Field, ConfigDict


class ReportSummaryVO(BaseModel):
    """
    报告摘要
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    total_cost: Optional[float] = Field(None, description="总成本(USD)", alias="totalCost")
    avg_response_time_ms: Optional[float] = Field(None, description="平均响应时间(毫秒)", alias="avgResponseTimeMs")
    total_tokens: Optional[int] = Field(None, description="总Token消耗", alias="totalTokens")
    total_results: Optional[int] = Field(None, description="测试结果总数", alias="totalResults")
    model_count: Optional[int] = Field(None, description="参与测试的模型数量", alias="modelCount")


class ModelStatisticsVO(BaseModel):
    """
    模型统计信息
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    model_name: str = Field(..., description="模型名称", alias="modelName")
    test_count: int = Field(..., description="测试次数", alias="testCount")
    avg_response_time_ms: Optional[float] = Field(None, description="平均响应时间(毫秒)", alias="avgResponseTimeMs")
    avg_input_tokens: Optional[float] = Field(None, description="平均输入Token数", alias="avgInputTokens")
    avg_output_tokens: Optional[float] = Field(None, description="平均输出Token数", alias="avgOutputTokens")
    total_tokens: Optional[int] = Field(None, description="总Token数", alias="totalTokens")
    total_cost: Optional[float] = Field(None, description="总成本(USD)", alias="totalCost")
    avg_cost: Optional[float] = Field(None, description="平均成本(USD)", alias="avgCost")
    avg_user_rating: Optional[float] = Field(None, description="平均用户评分(1-5)", alias="avgUserRating")
    avg_ai_score: Optional[float] = Field(None, description="平均AI评分", alias="avgAiScore")


class RadarSeriesVO(BaseModel):
    """
    雷达图系列数据
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    model_name: str = Field(..., description="模型名称", alias="modelName")
    values: List[float] = Field(..., description="各维度数值")


class RadarChartDataVO(BaseModel):
    """
    雷达图数据
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    dimensions: List[str] = Field(..., description="维度名称列表")
    series: List[RadarSeriesVO] = Field(..., description="各模型数据系列")


class BarSeriesVO(BaseModel):
    """
    柱状图系列数据
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    name: str = Field(..., description="系列名称")
    data: List[float] = Field(..., description="数据值列表")
    unit: Optional[str] = Field(None, description="单位")


class BarChartDataVO(BaseModel):
    """
    柱状图数据
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    categories: List[str] = Field(..., description="X轴标签(模型名称)")
    series: List[BarSeriesVO] = Field(..., description="数据系列列表")


class TestResultVO(BaseModel):
    """
    测试结果视图
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    id: str = Field(..., description="结果唯一标识")
    task_id: str = Field(..., description="任务ID", alias="taskId")
    scene_id: str = Field(..., description="场景ID", alias="sceneId")
    prompt_id: str = Field(..., description="提示词ID", alias="promptId")
    model_name: str = Field(..., description="模型名称", alias="modelName")
    input_prompt: str = Field(..., description="输入提示词", alias="inputPrompt")
    output_text: str = Field(..., description="输出内容", alias="outputText")
    reasoning: Optional[str] = Field(None, description="思考过程")
    response_time_ms: Optional[int] = Field(None, description="响应时间(毫秒)", alias="responseTimeMs")
    input_tokens: Optional[int] = Field(None, description="输入Token数", alias="inputTokens")
    output_tokens: Optional[int] = Field(None, description="输出Token数", alias="outputTokens")
    cost: Optional[float] = Field(None, description="成本(USD)")
    user_rating: Optional[int] = Field(None, description="用户评分(1-5)", alias="userRating")
    ai_score: Optional[str] = Field(None, description="AI评分详情(JSON)", alias="aiScore")
    create_time: Optional[str] = Field(None, description="创建时间", alias="createTime")


class ReportVO(BaseModel):
    """
    测试报告
    """
    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
        protected_namespaces=(),
    )

    task_id: str = Field(..., description="任务ID", alias="taskId")
    task_name: Optional[str] = Field(None, description="任务名称", alias="taskName")
    summary: ReportSummaryVO = Field(..., description="报告摘要")
    model_statistics: List[ModelStatisticsVO] = Field(..., description="各模型统计", alias="modelStatistics")
    radar_chart: RadarChartDataVO = Field(..., description="雷达图数据", alias="radarChart")
    bar_chart: BarChartDataVO = Field(..., description="柱状图数据", alias="barChart")
    test_results: List[TestResultVO] = Field(..., description="详细测试结果列表", alias="testResults")
