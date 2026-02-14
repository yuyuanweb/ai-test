"""
缓存常量
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""


class CacheConstant:
    """
    缓存常量
    """

    MODEL_PRICING_CACHE_NAME = "modelPricing"

    MODEL_PRICING_KEY_PREFIX = "model:pricing:"

    MODEL_PRICING_TTL_HOURS = 24

    USER_DAILY_COST_KEY_PREFIX = "user:cost:daily:"

    USER_MONTHLY_COST_KEY_PREFIX = "user:cost:monthly:"

    USER_DAILY_COST_TTL_HOURS = 25

    USER_MONTHLY_COST_TTL_DAYS = 32

    STATISTICS_COST_KEY_PREFIX = "statistics:cost:"

    STATISTICS_USAGE_KEY_PREFIX = "statistics:usage:"

    STATISTICS_PERFORMANCE_KEY_PREFIX = "statistics:performance:"

    STATISTICS_TTL_MINUTES = 5
