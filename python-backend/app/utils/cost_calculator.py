"""
成本计算工具
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
from decimal import Decimal
from typing import Optional


class CostCalculator:
    """
    成本计算器
    """
    
    @staticmethod
    def calculate_cost(
        model_id: str,
        input_tokens: int,
        output_tokens: int,
        input_price: Optional[Decimal] = None,
        output_price: Optional[Decimal] = None
    ) -> Decimal:
        """
        计算成本
        
        Args:
            model_id: 模型ID
            input_tokens: 输入Token数
            output_tokens: 输出Token数
            input_price: 输入价格（每百万tokens，美元），如果为None则从数据库查询
            output_price: 输出价格（每百万tokens，美元），如果为None则从数据库查询
            
        Returns:
            成本（美元）
        """
        if input_price is None or output_price is None:
            return Decimal('0')
        
        input_cost = (Decimal(str(input_tokens)) / Decimal('1000000')) * input_price
        output_cost = (Decimal(str(output_tokens)) / Decimal('1000000')) * output_price
        
        total_cost = input_cost + output_cost
        
        return total_cost.quantize(Decimal('0.000001'))
    
    @staticmethod
    def estimate_tokens(text: str) -> int:
        """
        估算文本的Token数（简单估算：1 token ≈ 4 个字符）
        
        Args:
            text: 文本内容
            
        Returns:
            估算的Token数
        """
        if not text:
            return 0
        return max(1, len(text) // 4)
