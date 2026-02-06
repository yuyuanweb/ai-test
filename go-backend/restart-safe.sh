#!/bin/bash
# 安全重启 Go 服务脚本 - 只杀掉 go-backend 相关进程

echo "🔍 查找 go-backend 相关进程..."

# 查找并杀掉 go-backend/bin/server 进程
SERVER_PID=$(ps aux | grep "go-backend/bin/server" | grep -v grep | awk '{print $2}')
if [ -n "$SERVER_PID" ]; then
    echo "⚠️  发现 go-backend 服务进程: PID=$SERVER_PID"
    kill -9 $SERVER_PID
    echo "✅ 已停止 go-backend 服务"
else
    echo "ℹ️  未找到运行中的 go-backend 服务"
fi

# 查找并杀掉 air 进程（仅限 go-backend 目录下的）
AIR_PID=$(ps aux | grep "go-backend.*air" | grep -v grep | awk '{print $2}')
if [ -n "$AIR_PID" ]; then
    echo "⚠️  发现 air 进程: PID=$AIR_PID"
    kill -9 $AIR_PID
    echo "✅ 已停止 air"
else
    echo "ℹ️  未找到运行中的 air"
fi

# 清理可能占用 8080 端口的进程（仅限当前用户的进程）
PORT_8080_PID=$(lsof -ti :8080 2>/dev/null | head -1)
if [ -n "$PORT_8080_PID" ]; then
    PORT_PROCESS=$(ps -p $PORT_8080_PID -o comm= 2>/dev/null)
    if [[ "$PORT_PROCESS" == *"server"* ]] || [[ "$PORT_PROCESS" == *"air"* ]]; then
        echo "⚠️  发现占用 8080 端口的进程: PID=$PORT_8080_PID ($PORT_PROCESS)"
        kill -9 $PORT_8080_PID
        echo "✅ 已释放 8080 端口"
    else
        echo "⚠️  警告：8080 端口被其他程序占用: $PORT_PROCESS (PID=$PORT_8080_PID)"
        echo "❌ 请手动处理该进程"
        exit 1
    fi
fi

# 清理旧的二进制文件
echo "🧹 清理旧文件..."
rm -rf bin/server
go clean -cache

# 重新编译
echo "🔨 开始编译..."
go build -o bin/server ./cmd/server
if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

# 启动服务
echo "🚀 启动服务..."
./bin/server > /tmp/go-sockjs.log 2>&1 &
SERVER_PID=$!

# 等待启动
sleep 2

# 检查是否启动成功
if ps -p $SERVER_PID > /dev/null; then
    echo "✅ 服务启动成功: PID=$SERVER_PID"
    echo "📝 日志文件: /tmp/go-sockjs.log"
else
    echo "❌ 服务启动失败，请查看日志"
    exit 1
fi
