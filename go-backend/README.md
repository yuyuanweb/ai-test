# AI 大模型评测平台 - Go 版本

## 项目简介

这是 AI 大模型评测平台的 Go 语言版本后端，采用 Gin + GORM + Redis 技术栈。

## 技术栈

- **Go**: 1.22+
- **Web 框架**: Gin 1.11+
- **ORM**: GORM 1.31+
- **数据库**: MySQL 8.0+
- **缓存**: Redis 7.x
- **配置管理**: Viper

## 项目结构

```
go-backend/
├── cmd/
│   └── server/
│       └── main.go                 # 程序入口
├── internal/
│   ├── handler/                    # HTTP处理器(Controller)
│   ├── service/                    # 业务逻辑层
│   ├── repository/                 # 数据访问层
│   ├── model/                      # 数据模型
│   ├── middleware/                 # 中间件
│   └── config/                     # 配置管理
├── pkg/                            # 公共库
│   ├── utils/
│   ├── constants/
│   └── errors/
├── config/
│   └── config.yaml                 # 配置文件
├── sql/                            # SQL脚本
├── migrations/                     # 数据库迁移
├── go.mod                          # Go依赖管理
└── README.md
```

## 快速开始

### 1. 环境准备

- Go 1.22+
- MySQL 8.0+
- Redis 7.x

### 2. 安装依赖

```bash
go mod download
```

### 3. 配置文件

复制 `config/config.yaml` 并修改数据库配置：

```yaml
database:
  host: localhost
  port: 3306
  user: root
  password: your_password
  dbname: ai_eval
```

### 4. 初始化数据库

执行项目根目录 `ai-test/sql/create_table.sql` 中的 SQL 脚本。

### 5. 启动项目

```bash
go run cmd/server/main.go
```

服务器将在 http://localhost:8080 启动。

### 6. 测试接口

```bash
curl http://localhost:8080/api/health
```

预期返回：
```json
{
  "code": 0,
  "message": "success",
  "data": "Hello World! AI大模型评测平台 Go版 运行正常"
}
```

## 开发规范

- 每个文件开头添加注释，包含作者信息
- 使用 gofmt 格式化代码
- 遵循 Go 代码规范
- 提交信息采用约定式提交规范

## 作者

<a href="https://codefather.cn">编程导航学习圈</a>
