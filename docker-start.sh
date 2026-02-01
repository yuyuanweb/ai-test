#!/bin/bash

# Docker 启动脚本
# 使用方法: ./docker-start.sh

echo "开始构建和启动 Docker 容器..."

# 设置端口（可根据需要修改）
BACKEND_PORT=8123
FRONTEND_PORT=8090

# 1. 构建镜像
echo "正在构建后端镜像..."
docker build -t ai-test-backend .

echo "正在构建前端镜像..."
docker build -t ai-test-frontend ./frontend

# 2. 创建网络
echo "创建 Docker 网络..."
docker network create ai-test-network 2>/dev/null || echo "网络已存在"

# 3. 停止并删除旧容器
echo "清理旧容器..."
docker rm -f backend frontend 2>/dev/null || true

# 4. 启动后端容器
echo "启动后端容器（端口 ${BACKEND_PORT}）..."
docker run -d \
  --name backend \
  --network ai-test-network \
  -p ${BACKEND_PORT}:8123 \
  --add-host host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=prod \
  ai-test-backend

# 5. 启动前端容器
echo "启动前端容器（端口 ${FRONTEND_PORT}）..."
docker run -d \
  --name frontend \
  --network ai-test-network \
  -p ${FRONTEND_PORT}:80 \
  ai-test-frontend

# 6. 显示状态
echo ""
echo "============================================"
echo "启动完成！"
echo "============================================"
echo "前端访问地址: http://localhost:${FRONTEND_PORT}"
echo "后端 API: http://localhost:${BACKEND_PORT}/api"
echo "API 文档: http://localhost:${BACKEND_PORT}/api/doc.html"
echo ""
echo "查看日志:"
echo "  后端: docker logs -f backend"
echo "  前端: docker logs -f frontend"
echo ""
docker ps
