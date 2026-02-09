"""
报告服务层：生成多维度对比报告、数据统计、雷达图/柱状图数据
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import json
import logging
from collections import defaultdict
from typing import List, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import BusinessException, ErrorCode
from app.models.test_result import TestResult
from app.models.test_task import TestTask
from app.schemas.report import (
    ReportVO,
    ReportSummaryVO,
    ModelStatisticsVO,
    RadarChartDataVO,
    RadarSeriesVO,
    BarChartDataVO,
    BarSeriesVO,
    TestResultVO,
)

logger = logging.getLogger(__name__)

RADAR_DIMENSIONS = ["准确性", "完整性", "速度", "成本效率", "用户满意度"]
SPEED_NORMALIZE_DIVISOR = 10000.0
COST_EFFICIENCY_FACTOR = 100.0
COST_EFFICIENCY_OFFSET = 0.01
COMPLETENESS_MAX = 100.0
SCORE_MAX = 100.0


class ReportService:
    """
    报告服务：生成测试报告（摘要、模型统计、雷达图、柱状图、详细结果）
    """

    @staticmethod
    async def generate_report(db: AsyncSession, task_id: str, user_id: int) -> ReportVO:
        """
        生成测试报告

        Args:
            db: 数据库会话
            task_id: 任务ID
            user_id: 当前用户ID

        Returns:
            ReportVO 报告数据

        Raises:
            BusinessException: 任务不存在、无权限、无测试结果
        """
        if not task_id or not str(task_id).strip():
            raise BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不能为空")

        task_result = await db.execute(
            select(TestTask).where(TestTask.id == task_id.strip(), TestTask.is_delete == 0)
        )
        task = task_result.scalar_one_or_none()
        if not task:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在")
        if task.user_id != user_id:
            raise BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该任务报告")

        results_result = await db.execute(
            select(TestResult)
            .where(TestResult.task_id == task_id.strip(), TestResult.is_delete == 0)
            .order_by(TestResult.create_time.asc())
        )
        test_results: List[TestResult] = list(results_result.scalars().all())

        if not test_results:
            raise BusinessException(ErrorCode.NOT_FOUND_ERROR, "该任务暂无测试结果")

        summary = ReportService._calculate_summary(test_results)
        model_statistics = ReportService._calculate_model_statistics(test_results)
        radar_chart = ReportService._generate_radar_chart(test_results, model_statistics)
        bar_chart = ReportService._generate_bar_chart(model_statistics)
        test_result_vo_list = [ReportService._to_test_result_vo(r) for r in test_results]

        return ReportVO(
            task_id=task.id,
            task_name=task.name,
            summary=summary,
            model_statistics=model_statistics,
            radar_chart=radar_chart,
            bar_chart=bar_chart,
            test_results=test_result_vo_list,
        )

    @staticmethod
    def _calculate_summary(test_results: List[TestResult]) -> ReportSummaryVO:
        """计算报告摘要"""
        total_cost_float = sum(
            float(r.cost) for r in test_results if r.cost is not None
        ) or None

        response_times = [r.response_time_ms for r in test_results if r.response_time_ms is not None]
        avg_response_ms = sum(response_times) / len(response_times) if response_times else None

        total_tokens = sum(
            (r.input_tokens or 0) + (r.output_tokens or 0)
            for r in test_results
            if (r.input_tokens is not None or r.output_tokens is not None)
        )

        model_names = {r.model_name for r in test_results}
        return ReportSummaryVO(
            total_cost=total_cost_float,
            avg_response_time_ms=avg_response_ms,
            total_tokens=total_tokens,
            total_results=len(test_results),
            model_count=len(model_names),
        )

    @staticmethod
    def _calculate_model_statistics(test_results: List[TestResult]) -> List[ModelStatisticsVO]:
        """按模型分组计算统计信息"""
        grouped: dict[str, List[TestResult]] = defaultdict(list)
        for r in test_results:
            grouped[r.model_name].append(r)

        statistics_list: List[ModelStatisticsVO] = []
        for model_name, model_results in grouped.items():
            count = len(model_results)
            response_times = [r.response_time_ms for r in model_results if r.response_time_ms is not None]
            avg_response_ms = sum(response_times) / len(response_times) if response_times else None

            input_tokens = [r.input_tokens for r in model_results if r.input_tokens is not None]
            avg_input = sum(input_tokens) / len(input_tokens) if input_tokens else None
            output_tokens = [r.output_tokens for r in model_results if r.output_tokens is not None]
            avg_output = sum(output_tokens) / len(output_tokens) if output_tokens else None

            total_tokens = sum(
                (r.input_tokens or 0) + (r.output_tokens or 0)
                for r in model_results
            )
            total_cost_float = sum(
                float(r.cost) for r in model_results if r.cost is not None
            )
            avg_cost = total_cost_float / count if count else None

            user_ratings = [r.user_rating for r in model_results if r.user_rating is not None]
            avg_user_rating = sum(user_ratings) / len(user_ratings) if user_ratings else None

            ai_scores: List[float] = []
            for r in model_results:
                if not r.ai_score or not str(r.ai_score).strip():
                    continue
                try:
                    obj = json.loads(r.ai_score) if isinstance(r.ai_score, str) else r.ai_score
                    total_val = obj.get("total")
                    if total_val is not None and isinstance(total_val, (int, float)):
                        if float(total_val) > 0:
                            ai_scores.append(float(total_val))
                except (json.JSONDecodeError, TypeError) as e:
                    logger.warning("解析AI评分失败: %s, %s", r.ai_score, e)
            avg_ai_score = sum(ai_scores) / len(ai_scores) if ai_scores else None

            statistics_list.append(
                ModelStatisticsVO(
                    model_name=model_name,
                    test_count=count,
                    avg_response_time_ms=avg_response_ms,
                    avg_input_tokens=avg_input,
                    avg_output_tokens=avg_output,
                    total_tokens=total_tokens,
                    total_cost=total_cost_float if total_cost_float else None,
                    avg_cost=avg_cost,
                    avg_user_rating=avg_user_rating,
                    avg_ai_score=avg_ai_score,
                )
            )
        return statistics_list

    @staticmethod
    def _generate_radar_chart(
        test_results: List[TestResult],
        model_statistics: List[ModelStatisticsVO],
    ) -> RadarChartDataVO:
        """生成雷达图数据（准确性、完整性、速度、成本效率、用户满意度）"""
        series_list: List[RadarSeriesVO] = []
        for stat in model_statistics:
            accuracy = stat.avg_ai_score if stat.avg_ai_score is not None else 0.0
            accuracy_norm = ReportService._normalize_score(accuracy, 0.0, 100.0)

            completeness = ReportService._calculate_completeness(test_results, stat.model_name)
            completeness_norm = ReportService._normalize_score(completeness, 0.0, COMPLETENESS_MAX)

            speed = 0.0
            if stat.avg_response_time_ms is not None and stat.avg_response_time_ms > 0:
                speed = min(SCORE_MAX, SPEED_NORMALIZE_DIVISOR / stat.avg_response_time_ms)

            cost_eff = 0.0
            if stat.avg_cost is not None and stat.avg_cost > 0:
                cost_eff = min(
                    SCORE_MAX,
                    (1.0 / (stat.avg_cost * COST_EFFICIENCY_FACTOR + COST_EFFICIENCY_OFFSET)) * 100.0,
                )

            user_sat = 0.0
            if stat.avg_user_rating is not None:
                user_sat = ReportService._normalize_score(stat.avg_user_rating, 1.0, 5.0)

            series_list.append(
                RadarSeriesVO(
                    model_name=stat.model_name,
                    values=[accuracy_norm, completeness_norm, speed, cost_eff, user_sat],
                )
            )
        return RadarChartDataVO(dimensions=RADAR_DIMENSIONS, series=series_list)

    @staticmethod
    def _calculate_completeness(test_results: List[TestResult], model_name: str) -> float:
        """基于输出文本长度和 Token 数计算完整性得分"""
        model_results = [r for r in test_results if r.model_name == model_name]
        if not model_results:
            return 0.0
        lengths = [len(r.output_text or "") for r in model_results if r.output_text is not None]
        avg_len = sum(lengths) / len(lengths) if lengths else 0.0
        tokens = [r.output_tokens for r in model_results if r.output_tokens is not None]
        avg_tokens = sum(tokens) / len(tokens) if tokens else 0.0
        completeness = (avg_len / 1000.0 + avg_tokens / 100.0) / 2.0
        return min(completeness, COMPLETENESS_MAX)

    @staticmethod
    def _normalize_score(value: float, min_val: float, max_val: float) -> float:
        """将分数标准化到 0-100"""
        if max_val == min_val:
            return 0.0
        normalized = ((value - min_val) / (max_val - min_val)) * 100.0
        return max(0.0, min(SCORE_MAX, normalized))

    @staticmethod
    def _generate_bar_chart(model_statistics: List[ModelStatisticsVO]) -> BarChartDataVO:
        """生成柱状图数据（平均响应时间、总Token、总成本）"""
        categories = [s.model_name for s in model_statistics]
        response_times = [
            s.avg_response_time_ms if s.avg_response_time_ms is not None else 0.0
            for s in model_statistics
        ]
        total_tokens = [
            float(s.total_tokens) if s.total_tokens is not None else 0.0
            for s in model_statistics
        ]
        total_costs = [
            s.total_cost if s.total_cost is not None else 0.0
            for s in model_statistics
        ]
        series_list = [
            BarSeriesVO(name="平均响应时间", data=response_times, unit="ms"),
            BarSeriesVO(name="总Token消耗", data=total_tokens, unit="tokens"),
            BarSeriesVO(name="总成本", data=total_costs, unit="USD"),
        ]
        return BarChartDataVO(categories=categories, series=series_list)

    @staticmethod
    def _to_test_result_vo(r: TestResult) -> TestResultVO:
        """TestResult 转 TestResultVO"""
        cost_float = float(r.cost) if r.cost is not None else None
        ai_score_str = r.ai_score if isinstance(r.ai_score, str) else (json.dumps(r.ai_score) if r.ai_score else None)
        create_str = r.create_time.isoformat() if r.create_time else None
        return TestResultVO(
            id=r.id,
            task_id=r.task_id,
            scene_id=r.scene_id,
            prompt_id=r.prompt_id,
            model_name=r.model_name,
            input_prompt=r.input_prompt or "",
            output_text=r.output_text or "",
            reasoning=r.reasoning,
            response_time_ms=r.response_time_ms,
            input_tokens=r.input_tokens,
            output_tokens=r.output_tokens,
            cost=cost_float,
            user_rating=r.user_rating,
            ai_score=ai_score_str,
            create_time=create_str,
        )
