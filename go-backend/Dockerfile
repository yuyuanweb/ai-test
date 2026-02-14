# 构建阶段
FROM golang:1.24-alpine AS builder

WORKDIR /app

# 安装必要的依赖
RUN apk add --no-cache git

# 复制 go.mod 和 go.sum
COPY go.mod go.sum ./

# 下载依赖
RUN go mod download

# 复制源代码
COPY . .

# 构建应用
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main ./cmd/server

# 运行阶段
FROM alpine:latest

RUN apk --no-cache add ca-certificates curl

WORKDIR /root/

# 从构建阶段复制二进制文件
COPY --from=builder /app/main .
COPY --from=builder /app/config ./config

# 单容器直接 run 时默认连主机服务（docker-compose 会用 environment 覆盖）
ENV SERVER_MODE=release
ENV DATABASE_HOST=host.docker.internal
ENV DATABASE_PORT=3306
ENV DATABASE_USER=root
ENV DATABASE_PASSWORD=11111111
ENV DATABASE_DBNAME=ai_eval
ENV REDIS_ADDR=host.docker.internal:6379
ENV REDIS_PASSWORD=111111
ENV RABBITMQ_HOST=host.docker.internal
ENV RABBITMQ_PORT=5672
ENV RABBITMQ_USERNAME=guest
ENV RABBITMQ_PASSWORD=guest

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/api/health || exit 1

CMD ["./main"]
