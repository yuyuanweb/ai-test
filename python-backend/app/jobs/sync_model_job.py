"""
模型同步定时任务
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import asyncio
from datetime import datetime
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from app.db.session import AsyncSessionLocal
from app.services.model_service import ModelService
from app.core.logging_config import logger


async def sync_models_job():
    """
    同步模型信息定时任务
    每天凌晨 2 点执行一次
    """
    try:
        logger.info("开始执行模型同步任务")
        
        async with AsyncSessionLocal() as db:
            model_service = ModelService(db)
            count = await model_service.sync_models_from_openrouter()
            
            logger.info(f"模型同步任务完成，同步了 {count} 个模型")
    
    except Exception as e:
        logger.error(f"模型同步任务失败: {str(e)}", exc_info=True)


def start_scheduler():
    """
    启动定时任务调度器
    """
    scheduler = AsyncIOScheduler()
    
    scheduler.add_job(
        sync_models_job,
        trigger=CronTrigger(hour=2, minute=0),
        id="sync_models_job",
        name="同步模型信息",
        replace_existing=True
    )
    
    scheduler.start()
    logger.info("定时任务调度器已启动")
    
    return scheduler
