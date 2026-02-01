#!/bin/bash

# Docker 停止脚本
# 使用方法: ./docker-stop.sh

echo "停止 Docker 容器..."

# 停止容器
docker stop backend frontend 2>/dev/null || true

# 删除容器
docker rm backend frontend 2>/dev/null || true

echo "容器已停止并删除"
echo ""
echo "如需删除镜像和网络，运行:"
echo "  docker rmi ai-test-backend ai-test-frontend"
echo "  docker network rm ai-test-network"
