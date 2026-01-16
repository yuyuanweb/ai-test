# AI 大模型评测平台

> 作者：[编程导航学习圈](https://codefather.cn)

## 项目简介

一个基于 Spring Boot 3.5 + Vue 3 的AI大模型评测平台，支持多模型对比测试、提示词优化、性能分析和可视化报告生成。

## 核心功能

- ✅ 多模型并排对比（Side-by-Side）
- ✅ 单模型多轮对话（Direct Chat）
- ✅ 实时Token统计与成本计算
- ✅ 流式响应（打字机效果）
- ✅ 代码沙箱预览
- ✅ 批量测试与报告生成
- ✅ 提示词优化建议

## 技术栈

### 后端

- Spring Boot 3.5.9
- Spring AI 1.1+（AI框架）
- MyBatis-Flex 1.11.1（数据库ORM）
- MySQL 8.0+（数据库）
- Redis 7.x（缓存 + Session）
- Redisson 3.50.0（分布式锁）
- RabbitMQ 3.12+（消息队列）
- OpenRouter（模型网关）
- Knife4j 4.4.0（接口文档）
- Hutool 5.8.43（工具库）

### 前端

- Vue 3.5.17
- TypeScript 5.8.0
- Vite 7.0.0
- Ant Design Vue 4.2.6
- Pinia 3.0.3
- Vue Router 4.5.1
- Axios 1.11.0
- ECharts 5.x（图表）
- Monaco Editor（代码编辑器）

## 项目结构

```
├── frontend/                 # 前端项目
│   ├── src/
│   │   ├── api/             # API 接口
│   │   ├── components/      # 公共组件
│   │   ├── config/          # 配置文件
│   │   ├── layouts/         # 布局组件
│   │   ├── pages/           # 页面
│   │   ├── router/          # 路由配置
│   │   └── stores/          # 状态管理
│   ├── Dockerfile           # 前端 Docker 配置
│   └── package.json
├── sql/                      # SQL 脚本
│   └── create_table.sql     # 建表语句
├── src/                      # 后端源码
│   └── main/
│       ├── java/com/yupi/template/
│       │   ├── annotation/  # 自定义注解
│       │   ├── aop/         # AOP 切面
│       │   ├── common/      # 通用类
│       │   ├── config/      # 配置类
│       │   ├── constant/    # 常量
│       │   ├── controller/  # 控制器
│       │   ├── exception/   # 异常处理
│       │   ├── mapper/      # 数据层
│       │   ├── model/       # 数据模型
│       │   ├── service/     # 业务逻辑
│       │   └── utils/       # 工具类
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           └── application-prod.yml
├── Dockerfile               # 后端 Docker 配置
├── docker-compose.yml       # Docker Compose 一键部署
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 21+
- Node.js 22+
- MySQL 8.0+
- Redis 7.0+

### 后端启动

1. 执行 `sql/create_table.sql` 创建数据库和表
2. 修改 `application-local.yml` 中的数据库和 Redis 配置
3. 配置 OpenRouter API Key
4. 运行 `MainApplication` 启动后端服务
5. 访问 http://localhost:8123/api/doc.html 查看接口文档

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 查看前端页面

## Docker 部署

### 一键部署（推荐）

```bash
docker-compose up -d
```

访问：
- 前端：http://localhost
- 后端 API：http://localhost:8123/api
- 接口文档：http://localhost:8123/api/doc.html

### 单独构建

```bash
# 构建后端
docker build -t ai-eval-backend .

# 构建前端
cd frontend
docker build -t ai-eval-frontend .
```

## 作者

[编程导航学习圈](https://codefather.cn)

## 许可证

本项目仅供学习交流使用
