"""
日志配置模块
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import logging
import os
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path


class LoggingConfig:
    """日志配置类"""
    
    LOG_DIR = "logs"
    MAX_BYTES = 30 * 1024 * 1024
    BACKUP_COUNT = 30
    
    LOG_FORMAT = "%(asctime)s [%(name)s] [%(levelname)s] - %(message)s"
    DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
    
    @classmethod
    def setup_logging(cls, log_level: str = "INFO"):
        """配置日志系统"""
        Path(cls.LOG_DIR).mkdir(exist_ok=True)
        
        root_logger = logging.getLogger()
        root_logger.setLevel(getattr(logging, log_level.upper()))
        
        if root_logger.handlers:
            root_logger.handlers.clear()
        
        console_handler = cls._create_console_handler()
        root_logger.addHandler(console_handler)
        
        error_handler = cls._create_file_handler("error", logging.ERROR)
        warn_handler = cls._create_file_handler("warn", logging.WARNING)
        info_handler = cls._create_file_handler("info", logging.INFO)
        debug_handler = cls._create_file_handler("debug", logging.DEBUG)
        
        root_logger.addHandler(error_handler)
        root_logger.addHandler(warn_handler)
        root_logger.addHandler(info_handler)
        root_logger.addHandler(debug_handler)
        
        logging.info("日志系统初始化完成")
    
    @classmethod
    def _create_console_handler(cls):
        """创建控制台日志处理器"""
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        formatter = logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] %(message)s",
            datefmt=cls.DATE_FORMAT
        )
        console_handler.setFormatter(formatter)
        
        return console_handler
    
    @classmethod
    def _create_file_handler(cls, level_name: str, level: int):
        """创建文件日志处理器（按日期归档）"""
        log_file = os.path.join(cls.LOG_DIR, f"project-{level_name}.log")
        
        file_handler = TimedRotatingFileHandler(
            filename=log_file,
            when="midnight",
            interval=1,
            backupCount=cls.BACKUP_COUNT,
            encoding="utf-8"
        )
        
        file_handler.setLevel(level)
        file_handler.suffix = "%Y%m%d"
        
        class LevelFilter(logging.Filter):
            def __init__(self, level):
                super().__init__()
                self.level = level
            
            def filter(self, record):
                return record.levelno == self.level
        
        file_handler.addFilter(LevelFilter(level))
        
        formatter = logging.Formatter(
            fmt=cls.LOG_FORMAT,
            datefmt=cls.DATE_FORMAT
        )
        file_handler.setFormatter(formatter)
        
        return file_handler
