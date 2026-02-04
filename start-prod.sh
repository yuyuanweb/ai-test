#!/bin/bash

# 生产环境启动脚本
# 强制使用 IPv4，避免 IPv6 连接超时问题

JAR_FILE="target/springboot-init-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到 jar 文件 $JAR_FILE"
    echo "请先运行: mvn clean package -DskipTests"
    exit 1
fi

echo "正在启动应用（生产环境）..."

# 关键参数说明：
# -Djava.net.preferIPv4Stack=true  强制使用 IPv4
# -Djava.net.preferIPv6Addresses=false  禁用 IPv6 优先
# -Xmx2g  最大堆内存 2G
# -Xms512m  初始堆内存 512M

java -jar "$JAR_FILE" \
  --spring.profiles.active=prod \
  -Djava.net.preferIPv4Stack=true \
  -Djava.net.preferIPv6Addresses=false \
  -Xmx2g \
  -Xms512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200

echo "应用已启动"
