# AI 大模型评测平台 - Python 后端

基于 FastAPI + SQLAlchemy + Redis 的 AI 大模型评测平台后端服务。

## 技术栈

- **Web框架**: FastAPI 0.115.0
- **Python版本**: Python 3.11+
- **数据库**: MySQL 8.0 + SQLAlchemy 2.0
- **缓存**: Redis 7.x
- **AI集成**: OpenRouter SDK + LangChain
- **异步**: asyncio + aiofiles

## 项目结构

```
python-backend/
├── app/
│   ├── api/          # API路由
│   ├── core/         # 核心配置
│   │   ├── config.py        # 配置管理
│   │   └── logging_config.py # 日志配置
│   ├── db/           # 数据库连接
│   │   ├── session.py       # 数据库会话
│   │   └── redis.py         # Redis连接
│   ├── models/       # 数据库模型
│   ├── schemas/      # Pydantic模型
│   ├── services/     # 业务逻辑
│   ├── utils/        # 工具函数
│   └── main.py       # 应用入口
├── logs/             # 日志文件目录
├── tests/            # 测试文件
├── requirements.txt  # 依赖列表
├── .env              # 环境变量
├── .env.example      # 环境变量示例
├── Dockerfile        # Docker镜像构建
└── README.md
```

## 快速开始

### 1. 安装依赖

```bash
# 使用 Python 3.11+
python3 -m pip install -r requirements.txt
```

### 2. 配置环境变量

复制 `.env.example` 到 `.env` 并修改配置：

```bash
cp .env.example .env
```

### 3. 启动服务

```bash
# 开发模式（支持热更新）
python -m app.main

# 或使用 uvicorn（手动指定热更新）
uvicorn app.main:app --reload --port 9090

# 生产模式（关闭热更新）
# 修改 .env 中 APP_DEBUG=false，然后运行
python -m app.main
```

**热更新说明**：
- 当 `APP_DEBUG=true` 时，自动启用热更新
- 修改代码后，服务会自动重启
- 类似于Java的Spring Boot DevTools功能
- 监听 `.py` 文件的变化

### 4. 访问接口文档

- Swagger UI: http://localhost:9090/api/docs
- ReDoc: http://localhost:9090/api/redoc

## Docker 部署

```bash
# 构建镜像
docker build -t ai-eval-backend .

# 运行容器
docker run -d -p 9090:9090 --name ai-eval-backend ai-eval-backend
```

## 健康检查

- 基础健康检查: GET /api/health
- 数据库健康检查: GET /api/health/db
- Redis健康检查: GET /api/health/redis

## 开发规范

- 每个文件开头添加作者信息
- 禁止使用行尾注释
- 禁止返回Map类型，使用封装对象
- 禁止使用魔法值常量
- 禁止使用emoji表情符号

## 日志说明

项目采用按级别和日期分离的日志管理策略：

- **日志目录**: `logs/`
- **日志级别**: DEBUG、INFO、WARN、ERROR（分别对应不同的日志文件）
- **日志格式**: `时间戳 [模块名] [级别] - 消息内容`
- **归档策略**: 每天午夜自动切换，旧日志文件按日期归档
- **保留时间**: 30天
- **日志文件**:
  - `project-debug.log` - DEBUG级别日志
  - `project-info.log` - INFO级别日志
  - `project-warn.log` - WARN级别日志
  - `project-error.log` - ERROR级别日志

## 作者

<a href="https://codefather.cn">编程导航学习圈</a>
